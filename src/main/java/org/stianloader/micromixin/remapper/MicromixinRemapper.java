package org.stianloader.micromixin.remapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import org.jetbrains.annotations.ApiStatus.OverrideOnly;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.stianloader.micromixin.remapper.selectors.AtSelector;
import org.stianloader.micromixin.remapper.selectors.HeadSelector;
import org.stianloader.micromixin.remapper.selectors.InvokeSelector;
import org.stianloader.micromixin.remapper.selectors.ReturnSelector;
import org.stianloader.micromixin.remapper.selectors.TailSelector;
import org.stianloader.remapper.MappingLookup;
import org.stianloader.remapper.MappingSink;
import org.stianloader.remapper.MemberRef;
import org.stianloader.remapper.Remapper;

public class MicromixinRemapper {

    @NotNull
    private static final String CALLBACK_INFO_CLASS = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
    @NotNull
    private static final String CALLBACK_INFO_RETURNABLE_CLASS = "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";

    @NotNull
    private final MemberLister lister;
    @NotNull
    private final MappingLookup lookup;
    @NotNull
    private final MappingSink sink;

    public MicromixinRemapper(@NotNull MappingLookup lookup, @NotNull MappingSink sink, @NotNull MemberLister lister) {
        this.lookup = lookup;
        this.sink = sink;
        this.lister = lister;
    }

    /**
     * Queries whether interface members may be renamed as a result of a {@link MicromixinRemapper#remapClass(ClassNode)}
     * pass. This method mainly exist as a way to prevent collateral damage when inappropriately implementing interfaces
     * when using annotations such as <code>&#64;Shadow</code> or <code>&#64;Overwrite</code> alongside the
     * {@link Override}.
     *
     * <p>The default implementation always returns <code>true</code> as an overly cautious way of weeding out potentially
     * inappropriate usages of mixins.
     *
     * @param name The internal name of the interface whose member may be modified
     * @param targets A collection of internal names of the targeted classes by the mixin (in case one wishes to perform hierarchy validation)
     * @return True if renaming it's members is forbidden, false if allowed.
     */
    protected boolean forbidRemappingInterfaceMembers(@NotNull String name, @NotNull Collection<@NotNull String> targets) {
        return true;
    }

    private void handleOverwrite(@Nullable AnnotationNode annot, @NotNull Collection<@NotNull String> targets, ClassNode node, MethodNode method) throws IllegalMixinException, MissingFeatureException {
        if (annot != null && annot.values != null) {
            for (int i = 0; i < annot.values.size(); i += 2) {
                String name = (String) annot.values.get(i);
                Object value = annot.values.get(i + 1);
                if (name.equals("aliases")) {
                    @SuppressWarnings("unchecked")
                    List<String> aliases = (List<String>) (List<?>) value;
                    for (int j = 0; j < aliases.size(); j++) {
                        String alias = aliases.get(j);
                        assert alias != null;
                        String remappedAlias = null;
                        for (String target : targets) {
                            if (this.lister.hasMemberInHierarchy(target, alias, method.desc)) {
                                String remappedName = this.lookup.getRemappedMethodName(target, alias, method.desc);
                                if (remappedAlias != null && !remappedAlias.equals(remappedName)) {
                                    throw new IllegalMixinException("Disjoint mapping names while trying to remap alias for @Overwrite-annotated method: " + node.name + "." + method.name + method.desc
                                            + ". This is likely caused by different target classes having different names for the overwritten member. Potential ways of resolving this issue include:\n"
                                            + "\t1. Splitting the mixin class so that each target class has it's own mixin.\n"
                                            + "\t2. Report this behaviour as unintended to the micromixin-remapper developers (please also include the mixin itself and a short statement on why the behaviour should change as well as what the new behaviour should be)");
                                }
                                remappedAlias = remappedName;
                            }
                        }
                        if (remappedAlias != null) {
                            aliases.set(j, remappedAlias);
                        }
                    }
                } else {
                    this.logUnimplementedFeature("Unimplemented key in @Overwrite: " + name + " within node " + node.name);
                }
            }
        }

        String remappedMemberName = null;
        for (String target : targets) {
            String targetRemapped = this.lookup.getRemappedMethodName(target, method.name, method.desc);
            if (remappedMemberName != null && !remappedMemberName.equals(targetRemapped)) {
                throw new IllegalMixinException("Disjoint mapping names while trying to remap name of (implicitly) @Overwrite-annotated method: " + node.name + "." + method.name + method.desc
                        + ". This is likely caused by different target classes having different names for the shadowed member. Potential ways of resolving this issue include:\n"
                        + "\t1. Splitting the mixin class so that each target class has it's own mixin.\n"
                        + "\t2. Use an @Invoker (not supported by micromixin as of April 2024)\n"
                        + "\t3. Report this behaviour as unintended to the micromixin-remapper developers (please also include the mixin itself and a short statement on why the behaviour should change)");
            }
            remappedMemberName = targetRemapped;
        }

        if (remappedMemberName != null && !method.name.equals(remappedMemberName)) {
            for (String itf : node.interfaces) {
                assert itf != null;
                if (!this.forbidRemappingInterfaceMembers(itf, targets)) {
                    continue;
                }

                if (this.lister.hasMemberInHierarchy(itf, method.name, method.desc)) {
                    throw new IllegalMixinException("Attempt to (implicitly) @Overwrite method " + node.name + "." + method.name + method.desc + " which is provided by the interface " + itf
                            + ". The interface does not allow remapping it's members (see MicromixinRemapper#forbidRemappingInterfaceMembers). Potential ways of resolving this issue include:\n"
                            + "\t1. Rename the method in the interface or alter it's descriptor.\n"
                            + "\t2. Do not implement the interface in the mixin.\n"
                            + "\t3. Report this behaviour as unintended to the micromixin-remapper developers (please also include the mixin itself and a short statement on why the behaviour should change)");
                }
            }
        }

        if (remappedMemberName != null) {
            this.sink.remapMember(new MemberRef(node.name, method.name, method.desc), remappedMemberName);
        }
    }

    /**
     * The error handler that is invoked whenever an unimplemented or unknown feature is encountered.
     *
     * <p>Being able to track down these cases may help narrow down why the remapper failed remapping something
     * or remapped something in an unexpected way.
     *
     * <p>By default it'll cause an {@link MissingFeatureException} to be thrown, but it is possible to overwrite this
     * behaviour with a logging call. In that case, the remapper will try to continue on a best-effort basis.
     *
     * @param featureDescription The description of the feature that is not implemented and what caused the issue to occur.
     * @throws MissingFeatureException Thrown if the error handler is configured to stop execution of the remapper in a
     * fail-fast manner.
     */
    @OverrideOnly
    protected void logUnimplementedFeature(@NotNull String featureDescription) throws MissingFeatureException {
        throw new MissingFeatureException(featureDescription);
    }

    @Nullable
    @MustBeInvokedByOverriders
    @Contract(pure = true)
    protected AtSelector lookupSelector(@NotNull String atValue) {
        switch (atValue) {
        case "org.spongepowered.asm.mixin.injection.points.MethodHead":
        case "HEAD":
            return HeadSelector.INSTANCE;
        case "org.spongepowered.asm.mixin.injection.points.BeforeInvoke":
        case "INVOKE":
            return InvokeSelector.INSTANCE;
        case "org.spongepowered.asm.mixin.injection.points.BeforeReturn":
        case "RETURN":
            return ReturnSelector.INSTANCE;
        case "org.spongepowered.asm.mixin.injection.points.BeforeFinalReturn":
        case "TAIL":
            return TailSelector.INSTANCE;
        default:
            return null;
        }
    }

    private void remapAt(@NotNull String owner, @NotNull String member, int ordinal, @NotNull Collection<String> targets, AnnotationNode annot) throws IllegalMixinException, MissingFeatureException {
        int idxValue = 0;
        int idxArgs = 0;
        int idxTarget = 0;
        int idxDesc = 0;

        for (int i = 0; i < annot.values.size(); i++) {
            String name = (String) annot.values.get(i++);
            if (name.equals("args")) {
                idxArgs = i;
            } else if (name.equals("value")) {
                idxValue = i;
            } else if (name.equals("desc")) {
                idxDesc = i;
            } else if (name.equals("slice")) {
                // Slices don't need to be remapped to my knowledge, nor are they relevant to the remapping process.
            } else if (name.equals("target")) {
                idxTarget = i;
            } else {
                String error = "An unexpected error occured while remapping @At annotation in " + owner + "." + member;
                error += ordinal < 0 ? ("[" + ordinal + "]: ") : ": ";
                this.logUnimplementedFeature(error + "Unimplemented key in @At: " + name);
            }
        }

        if (idxValue == 0) {
            String error = "An unexpected error occured while remapping @At annotation in " + owner + "." + member;
            error += ordinal < 0 ? ("[" + ordinal + "]: ") : ": ";
            throw new IllegalMixinException(error + "The annotation is missing the required element 'value'. This error is usually caused by improperly written ASM transformers generating the mixin improperly. Tip: Use tools such as Krakatau, javap and Recaf for troubleshooting faulty transformers!");
        }

        @SuppressWarnings("unchecked")
        List<String> args = idxArgs != 0 ? (List<String>) annot.values.get(idxArgs) : null;

        String value = (String) annot.values.get(idxValue);
        AtSelector selector = this.lookupSelector(Objects.requireNonNull(value));
        if (selector == null) {
            String error = "An unexpected error occured while remapping @At annotation in " + owner + "." + member;
            error += ordinal < 0 ? ("[" + ordinal + "]: ") : ": ";
            this.logUnimplementedFeature(error + "Unknown @At injection point selector value: " + value);
        } else {
            String errorPrefix = "An unexpected error occured while remapping @At annotation in " + owner + "." + member;
            errorPrefix += ordinal < 0 ? ("[" + ordinal + "]: ") : ": ";
            selector.remapArgs(errorPrefix, args, this.lookup);
        }

        if (idxTarget != 0) {
            String errorPrefix = "An unexpected error occured while remapping @At.target in " + owner + "." + member;
            errorPrefix += ordinal < 0 ? ("[" + ordinal + "]: ") : ": ";
            annot.values.set(idxTarget, this.remapTargetSelector(errorPrefix, (String) annot.values.get(idxTarget), null, null));
        }

        if (idxDesc != 0) {
            String errorPrefix = "An unexpected error occured while remapping @At.desc in " + owner + "." + member;
            errorPrefix += ordinal < 0 ? ("[" + ordinal + "]: ") : ": ";
            boolean matchFields = selector != null && selector.isMatchingFields();
            this.remapDescAnnotation(errorPrefix, targets, (AnnotationNode) annot.values.get(idxDesc), matchFields);
        }
    }

    private void remapAtArray(@NotNull String owner, @NotNull String method, @NotNull Collection<String> targets, Object nodes) throws IllegalMixinException, MissingFeatureException {
        int ordinal = 0;
        for (Object node : (Iterable<?>) nodes) {
            this.remapAt(owner, method, ordinal++, targets, (AnnotationNode) node);
        }
    }

    /**
     * Remap a {@link ClassNode} and all the member {@link MethodNode MethodNodes}
     * and {@link FieldNode FieldNodes} within the class. Note that in order for
     * the micromixin remapping process to work correctly and account for inter-class
     * relationships (mixin inheritance), <strong>this method needs to be executed before the
     * actual remapping process through {@link Remapper#remapNode(ClassNode, StringBuilder)}</strong>.
     * More concisely, this method expect that {@link Remapper#remapNode(ClassNode, StringBuilder)}
     * will be run after {@link MicromixinRemapper#remapClass(ClassNode)}.
     *
     * <p>Modifications to this remapper's underlying {@link MappingSink} instance (set through
     * the constructor) are expected to be applied to the {@link Remapper} instance.
     * It is highly recommended that the {@link MappingSink} is aware of class hierarchy remapping.
     * Additionally, it may be useful to be able to verify that the {@link MappingSink} instance
     * does not accidentally or not remap non-mixin classes. If a mapping request emitted
     * by this method (or any of the method is is delegating to) ends up remapping an unrelated class,
     * then the mixin should be considered illegal.
     *
     * <p>It is permissible for the provided mixin class to not be an <code>&#64;Mixin</code>-annotated
     * class, in which case the class is skipped.
     *
     * @param node The {@link ClassNode} to remap
     * @throws IllegalMixinException Thrown if the mixin contains illegal code (e.g. invalid targets in
     * the <code>&#64;Mixin</code> or <code>&#64;Shadow</code> collisions while implementing an interface).
     * @throws MissingFeatureException Thrown due to {@link #logUnimplementedFeature(String)}, this exception is
     * reserved for purposes where the remapper encounters mixin features it is not aware of at the current
     * point in time.
     */
    public void remapClass(@NotNull ClassNode node) throws IllegalMixinException, MissingFeatureException {
        Set<@NotNull String> targets = new LinkedHashSet<>();
        boolean mixinClass = false;

        if (node.invisibleAnnotations == null) {
            return;
        }
        for (AnnotationNode annot : node.invisibleAnnotations) {
            if (!annot.desc.startsWith("Lorg/spongepowered/asm/mixin/")) {
                continue;
            }
            if (annot.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) {
                mixinClass = true;
                for (int i = 0; i < annot.values.size(); i += 2) {
                    String name = (String) annot.values.get(i);
                    Object value = annot.values.get(i + 1);
                    if (name.equals("value")) {
                        @SuppressWarnings("unchecked")
                        List<Type> aev = (List<Type>) value;
                        int j = aev.size();
                        while (j-- != 0) {
                            Type target = aev.get(j);
                            String targetDesc = target.getDescriptor();
                            assert targetDesc != null;
                            if (targetDesc.codePointAt(0) != 'L') {
                                throw new IllegalMixinException("Mixin class " + node.name + " targets type " + targetDesc + ", which is not an L-type reference (arrays and primitives cannot be transformed and are illegal targets for mixins!)");
                            }
                            String originTarget = targetDesc.substring(1, targetDesc.length() - 1);
                            String remappedTarget = this.lookup.getRemappedClassNameFast(originTarget);
                            targets.add(originTarget);
                            if (remappedTarget != null) {
                                aev.set(j, Type.getType('L' + remappedTarget + ';'));
                            }
                        }
                    } else if (name.equals("targets")) {
                        @SuppressWarnings("unchecked")
                        List<String> aev = (List<String>) value;
                        int j = aev.size();
                        while (j-- != 0) {
                            String target = aev.get(j);
                            assert target != null;
                            targets.add(target.replace('.', '/'));
                            aev.set(j, this.lookup.getRemappedClassName(target));
                        }
                    } else if (!name.equals("priority")) {
                        this.logUnimplementedFeature("Unimplemented key in @Mixin: " + name + " within node " + node.name);
                    }
                }
            } else {
                this.logUnimplementedFeature("Unknown annotation at class level for node " + node.name + ": " + annot.desc);
            }
        }

        if (!mixinClass) {
            return;
        }

        for (MethodNode method : node.methods) {
            this.remapMethod(node, method, targets);
        }

        for (FieldNode field : node.fields) {
            this.remapField(node, field, targets);
        }
    }

    @NotNull
    private void remapDescAnnotation(@NotNull String errorPrefix, @NotNull Collection<String> targets, AnnotationNode descAnnot, boolean matchField) throws MissingFeatureException, IllegalMixinException {
        if (!descAnnot.desc.equals("Lorg/spongepowered/asm/mixin/injection/Desc;")) {
            throw new IllegalMixinException(errorPrefix + "Invalid annotation descriptor: " + descAnnot.desc);
        }

        int idxValue = 0;
        int idxArgs = 0;
        int idxRet = 0;
        int idxOwner = 0;

        for (int i = 0; i < descAnnot.values.size(); i++) {
            String name = (String) descAnnot.values.get(i++);
            if (name.equals("args")) {
                idxArgs = i;
            } else if (name.equals("value")) {
                idxValue = i;
            } else if (name.equals("ret")) {
                idxRet = i;
            } else if (name.equals("owner")) {
                idxOwner = i;
            } else {
                this.logUnimplementedFeature(errorPrefix + "Unimplemented key in @Desc: " + name);
            }
        }

        if (idxValue == 0) {
            throw new IllegalMixinException(errorPrefix + "The @Desc annotation is missing the required element 'value'. This error is usually caused by improperly written ASM transformers generating the mixin improperly. Tip: Use tools such as Krakatau, javap and Recaf for troubleshooting faulty transformers!");
        }

        Collection<String> owners = targets;
        if (idxOwner != 0) {
            owners = Collections.singleton(((Type) descAnnot.values.get(idxOwner)).getInternalName());
        }

        String name = (String) descAnnot.values.get(idxValue);
        assert name != null;
        String desc;

        if (!matchField) {
            if (idxArgs == 0) {
                desc = "()";
            } else {
                desc = "(";
                for (Object arg : (Iterable<?>) descAnnot.values.get(idxArgs)) {
                    desc += ((Type) Objects.requireNonNull(arg)).getDescriptor();
                }
                desc += ")";
            }
            if (idxRet == 0) {
                desc += "V";
            } else {
                desc += ((Type) descAnnot.values.get(idxRet)).getDescriptor();
            }
        } else {
            if (idxRet == 0) {
                desc = "V";
                this.logUnimplementedFeature(errorPrefix + "The @Desc annotation is expected to match a field, but has not explicitly set the field descriptor using ret.");
            } else {
                desc = ((Type) descAnnot.values.get(idxRet)).getDescriptor();
            }
        }

        if (owners.size() == 1) {
            String owner = owners.iterator().next();
            assert owner != null;
            StringBuilder builder = new StringBuilder();
            Remapper.remapSignature(this.lookup, desc, builder);
            if (matchField) {
                descAnnot.values.set(idxValue, this.lookup.getRemappedFieldName(owner, name, desc));
                if (idxRet != 0) {
                    descAnnot.values.set(idxRet, Type.getType(builder.toString()));
                }
            } else {
                descAnnot.values.set(idxValue, this.lookup.getRemappedMethodName(owner, name, desc));
                desc = builder.toString();
                if (idxRet != 0) {
                    descAnnot.values.set(idxRet, Type.getType(desc.substring(desc.lastIndexOf(')') + 1)));
                }
                if (idxArgs != 0) {
                    descAnnot.values.set(idxArgs, new ArrayList<>(Arrays.asList(Type.getMethodType(desc).getArgumentTypes())));
                }
            }
            if (idxOwner != 0) {
                descAnnot.values.set(idxOwner, Type.getType(this.lookup.getRemappedClassName(owner)));
            }
        } else {
            String mappedName = null;
            for (String owner : owners) {
                assert owner != null;
                String newName;
                if (matchField) {
                    newName = this.lookup.getRemappedFieldName(owner, name, desc);
                } else {
                    newName = this.lookup.getRemappedMethodName(owner, name, desc);
                }

                if (mappedName == null) {
                    mappedName = newName;
                } else if (!mappedName.equals(newName)) {
                    throw new IllegalMixinException(errorPrefix + "Torn @Desc: Multiple potential owners define multiple potential names. Following steps can be taken to mitigate this issue:\n"
                            + "\t1.: Only define a single @Mixin.target/@Mixin.value per Mixin class.\n"
                            + "\t2.: Explicitly define @Desc.owner for this @Desc annotation (and if necessary seperate a single @Desc into multiple @Desc annotations).\n"
                            + "\t3.: Validate the name hierarchy used to remap the @Desc; ensuring that no two classes define different names for the same method.\n"
                            + "\t4.: Ask for guidance in the relevant support channels (though I am afraid we wouldn't be able to help you much - there is only a limited pool of options that are available in this scenario)");
                }
            }

            if (mappedName == null) {
                throw new IllegalMixinException(errorPrefix + "No owners exist that would influence this @Desc (did you forget specifying a target in the @Mixin annotation?).");
            }

            StringBuilder builder = new StringBuilder();
            Remapper.remapSignature(this.lookup, desc, builder);

            descAnnot.values.set(idxValue, mappedName);
            if (matchField) {
                if (idxRet != 0) {
                    descAnnot.values.set(idxRet, Type.getType(builder.toString()));
                }
            } else {
                desc = builder.toString();
                if (idxRet != 0) {
                    descAnnot.values.set(idxRet, Type.getType(desc.substring(desc.lastIndexOf(')') + 1)));
                }
                if (idxArgs != 0) {
                    descAnnot.values.set(idxArgs, new ArrayList<>(Arrays.asList(Type.getMethodType(desc).getArgumentTypes())));
                }
            }
        }
    }

    private void remapField(@NotNull ClassNode node, FieldNode field, @NotNull Collection<@NotNull String> targets) throws MissingFeatureException, IllegalMixinException {
        String mainAnnotation = null;

        if (field.visibleAnnotations != null) {
            for (AnnotationNode annot : field.visibleAnnotations) {
                if (!annot.desc.startsWith("Lorg/spongepowered/asm/mixin/")
                        && !annot.desc.startsWith("Lcom/llamalad7/mixinextras/injector/")) {
                    continue;
                }

                if (annot.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;")) {
                    if (mainAnnotation != null) {
                        throw new IllegalMixinException("Illegal mixin field " + node.name + "." + field.name + ":" + field.desc + ": The mixin field is annotated with two or more incompatible annotations: " + mainAnnotation + " and " + annot.desc);
                    }
                    mainAnnotation = annot.desc;
                    String remapPrefix = "shadow$";
                    for (int i = 0; annot.values != null && i < annot.values.size(); i += 2) {
                        String name = (String) annot.values.get(i);
                        Object value = annot.values.get(i + 1);
                        if (name.equals("prefix")) {
                            remapPrefix = (String) value;
                        } else if (name.equals("aliases")) {
                            @SuppressWarnings("unchecked")
                            List<String> aliases = (List<String>) (List<?>) value;
                            for (int j = 0; j < aliases.size(); j++) {
                                String alias = aliases.get(j);
                                assert alias != null;
                                String remappedAlias = null;
                                for (String target : targets) {
                                    if (this.lister.hasMemberInHierarchy(target, alias, field.desc)) {
                                        String remappedName = this.lookup.getRemappedFieldName(target, alias, field.desc);
                                        if (remappedAlias != null && !remappedAlias.equals(remappedName)) {
                                            throw new IllegalMixinException("Disjoint mapping names while trying to remap alias for @Shadow-annotated field: " + node.name + "." + field.name + ":" + field.desc
                                                    + ". This is likely caused by different target classes having different names for the shadowed member. Potential ways of resolving this issue include:\n"
                                                    + "\t1. Splitting the mixin class so that each target class has it's own mixin.\n"
                                                    + "\t2. Use an @Accessor (not supported by micromixin as of April 2024)\n"
                                                    + "\t3. Report this behaviour as unintended to the micromixin-remapper developers (please also include the mixin itself and a short statement on why the behaviour should change)");
                                        }
                                        remappedAlias = remappedName;
                                    }
                                }
                                if (remappedAlias != null) {
                                    aliases.set(j, remappedAlias);
                                }
                            }
                        } else {
                            this.logUnimplementedFeature("Unimplemented key in @Shadow: " + name + " within node " + node.name);
                        }
                    }

                    String shadowName = field.name;
                    boolean prefixed = field.name.startsWith(remapPrefix);
                    if (prefixed) {
                        shadowName = field.name.substring(remapPrefix.length());
                    }

                    String remappedShadowName = null;
                    for (String target : targets) {
                        String targetRemapped = this.lookup.getRemappedFieldName(target, shadowName, field.desc);
                        if (remappedShadowName != null && !remappedShadowName.equals(targetRemapped)) {
                            throw new IllegalMixinException("Disjoint mapping names while trying to remap name of @Shadow-annotated field: " + node.name + "." + field.name + ":" + field.desc
                                    + ". This is likely caused by different target classes having different names for the shadowed member. Potential ways of resolving this issue include:\n"
                                    + "\t1. Splitting the mixin class so that each target class has it's own mixin.\n"
                                    + "\t2. Use an @Accessor (not supported by micromixin as of April 2024)\n"
                                    + "\t3. Report this behaviour as unintended to the micromixin-remapper developers (please also include the mixin itself and a short statement on why the behaviour should change)");
                        }
                        remappedShadowName = targetRemapped;
                    }

                    if (remappedShadowName != null) {
                        if (prefixed) {
                            remappedShadowName = remapPrefix + remappedShadowName;
                        }
                        this.sink.remapMember(new MemberRef(node.name, field.name, field.desc), remappedShadowName);
                    }
                } else if (annot.desc.equals("Lorg/spongepowered/asm/mixin/Unique;")) {
                    if (mainAnnotation != null) {
                        throw new IllegalMixinException("Illegal mixin field " + node.name + "." + field.name + ":" + field.desc + ": The mixin field is annotated with two or more incompatible annotations: " + mainAnnotation + " and " + annot.desc);
                    }
                    mainAnnotation = annot.desc;
                    for (int i = 0; annot.values != null && i < annot.values.size(); i += 2) {
                        String name = (String) annot.values.get(i);
                        if (!name.equals("silent")) {
                            this.logUnimplementedFeature("Unimplemented key in @Unique: " + name + " within node " + node.name);
                        }
                    }
                    // @Unique requires no further changes
                }
            }
        }

        // TODO implement implicit field overlay/shadow/overwrite
    }

    private void remapMethod(@NotNull ClassNode node, MethodNode method, @NotNull Collection<@NotNull String> targets) throws MissingFeatureException, IllegalMixinException {
        String mainAnnotation = null;

        if (method.visibleAnnotations != null) {
            for (AnnotationNode annot : method.visibleAnnotations) {
                if (!annot.desc.startsWith("Lorg/spongepowered/asm/mixin/")
                        && !annot.desc.startsWith("Lcom/llamalad7/mixinextras/injector/")) {
                    continue;
                }

                if (annot.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;")) {
                    if (mainAnnotation != null) {
                        throw new IllegalMixinException("Illegal mixin method " + node.name + "." + method.name + method.desc + ": The mixin handler is annotated with two or more incompatible annotations: " + mainAnnotation + " and " + annot.desc);
                    }
                    mainAnnotation = annot.desc;
                    String remapPrefix = "shadow$";
                    for (int i = 0; annot.values != null && i < annot.values.size(); i += 2) {
                        String name = (String) annot.values.get(i);
                        Object value = annot.values.get(i + 1);
                        if (name.equals("prefix")) {
                            remapPrefix = (String) value;
                        } else if (name.equals("aliases")) {
                            @SuppressWarnings("unchecked")
                            List<String> aliases = (List<String>) (List<?>) value;
                            for (int j = 0; j < aliases.size(); j++) {
                                String alias = aliases.get(j);
                                assert alias != null;
                                String remappedAlias = null;
                                for (String target : targets) {
                                    if (this.lister.hasMemberInHierarchy(target, alias, method.desc)) {
                                        String remappedName = this.lookup.getRemappedMethodName(target, alias, method.desc);
                                        if (remappedAlias != null && !remappedAlias.equals(remappedName)) {
                                            throw new IllegalMixinException("Disjoint mapping names while trying to remap alias for @Shadow-annotated method: " + node.name + "." + method.name + method.desc
                                                    + ". This is likely caused by different target classes having different names for the shadowed member. Potential ways of resolving this issue include:\n"
                                                    + "\t1. Splitting the mixin class so that each target class has it's own mixin.\n"
                                                    + "\t2. Use an @Invoker (not supported by micromixin as of April 2024)\n"
                                                    + "\t3. Report this behaviour as unintended to the micromixin-remapper developers (please also include the mixin itself and a short statement on why the behaviour should change)");
                                        }
                                        remappedAlias = remappedName;
                                    }
                                }
                                if (remappedAlias != null) {
                                    aliases.set(j, remappedAlias);
                                }
                            }
                        } else {
                            this.logUnimplementedFeature("Unimplemented key in @Shadow: " + name + " within node " + node.name);
                        }
                    }

                    String shadowName = method.name;
                    if (method.name.startsWith(remapPrefix)) {
                        shadowName = method.name.substring(remapPrefix.length());
                    }

                    String remappedShadowName = null;
                    for (String target : targets) {
                        String targetRemapped = this.lookup.getRemappedMethodName(target, shadowName, method.desc);
                        if (remappedShadowName != null && !remappedShadowName.equals(targetRemapped)) {
                            throw new IllegalMixinException("Disjoint mapping names while trying to remap name of @Shadow-annotated method: " + node.name + "." + method.name + method.desc
                                    + ". This is likely caused by different target classes having different names for the shadowed member. Potential ways of resolving this issue include:\n"
                                    + "\t1. Splitting the mixin class so that each target class has it's own mixin.\n"
                                    + "\t2. Use an @Invoker (not supported by micromixin as of April 2024)\n"
                                    + "\t3. Report this behaviour as unintended to the micromixin-remapper developers (please also include the mixin itself and a short statement on why the behaviour should change)");
                        }
                        remappedShadowName = targetRemapped;
                    }

                    if (remappedShadowName != null && !shadowName.equals(remappedShadowName) && method.name.equals(shadowName)) {
                        for (String itf : node.interfaces) {
                            assert itf != null;
                            if (!this.forbidRemappingInterfaceMembers(itf, targets)) {
                                continue;
                            }

                            if (this.lister.hasMemberInHierarchy(itf, method.name, method.desc)) {
                                throw new IllegalMixinException("Attempt to @Shadow method " + node.name + "." + method.name + method.desc + " which is provided by the interface " + itf
                                        + ". The interface does not allow remapping it's members (see MicromixinRemapper#forbidRemappingInterfaceMembers). Potential ways of resolving this issue include:\n"
                                        + "\t1. Rename the method in the interface or alter it's descriptor.\n"
                                        + "\t2. Do not implement the interface in the mixin.\n"
                                        + "\t3. Use an @Invoker (not supported by micromixin as of April 2024)\n"
                                        + "\t4. Use @Intrinsic (not supported by micromixin as of April 2024)\n"
                                        + "\t5. Use @Unique with silent = true\n"
                                        + "\t6. Report this behaviour as unintended to the micromixin-remapper developers (please also include the mixin itself and a short statement on why the behaviour should change)");
                            }
                        }
                    }

                    if (remappedShadowName != null) {
                        this.sink.remapMember(new MemberRef(node.name, method.name, method.desc), remapPrefix + remappedShadowName);
                    }
                } else if (annot.desc.equals("Lorg/spongepowered/asm/mixin/Unique;")) {
                    if (mainAnnotation != null) {
                        throw new IllegalMixinException("Illegal mixin method " + node.name + "." + method.name + method.desc + ": The mixin handler is annotated with two or more incompatible annotations: " + mainAnnotation + " and " + annot.desc);
                    }
                    mainAnnotation = annot.desc;
                    for (int i = 0; annot.values != null && i < annot.values.size(); i += 2) {
                        String name = (String) annot.values.get(i);
                        if (!name.equals("silent")) {
                            this.logUnimplementedFeature("Unimplemented key in @Unique: " + name + " within node " + node.name);
                        }
                    }

                    // @Unique requires no further changes
                } else if (annot.desc.equals("Lorg/spongepowered/asm/mixin/Overwrite;")) {
                    if (mainAnnotation != null) {
                        throw new IllegalMixinException("Illegal mixin method " + node.name + "." + method.name + method.desc + ": The mixin handler is annotated with two or more incompatible annotations: " + mainAnnotation + " and " + annot.desc);
                    }
                    mainAnnotation = annot.desc;
                    this.handleOverwrite(annot, targets, node, method);
                } else if (annot.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) {
                    if (mainAnnotation != null) {
                        throw new IllegalMixinException("Illegal mixin method " + node.name + "." + method.name + method.desc + ": The mixin handler is annotated with two or more incompatible annotations: " + mainAnnotation + " and " + annot.desc);
                    }
                    mainAnnotation = annot.desc;
                    for (int i = 0; i < annot.values.size(); i += 2) {
                        String name = (String) annot.values.get(i);
                        Object value = annot.values.get(i + 1);
                        if (name.equals("at")) {
                            this.remapAtArray(node.name, method.name + method.desc, targets, value);
                        } else if (name.equals("cancellable")
                                || name.equals("expect")
                                || name.equals("locals")
                                || name.equals("require")) {
                            // Nothing to do
                        } else if (name.equals("method") || name.equals("target")) {
                            Type[] arguments = Type.getArgumentTypes(method.desc);
                            boolean expectVoid = false;
                            boolean foundCallbackInfo = false;
                            int callbackInfoArgument;
                            for (callbackInfoArgument = 0; callbackInfoArgument < arguments.length; callbackInfoArgument++) {
                                Type arg = arguments[callbackInfoArgument];
                                String descriptor = arg.getDescriptor();
                                if ((expectVoid = descriptor.equals("L" + CALLBACK_INFO_CLASS + ";")) || descriptor.equals("L" + CALLBACK_INFO_RETURNABLE_CLASS + ";")) {
                                    foundCallbackInfo = true;
                                    break;
                                }
                            }
                            if (!foundCallbackInfo) {
                                throw new IllegalMixinException("@Inject annotated method " + node.name + "." + method.name + method.desc + " lacks type argument " + CALLBACK_INFO_CLASS + " or " + CALLBACK_INFO_RETURNABLE_CLASS);
                            }

                            final boolean expectVoidFinal = expectVoid; // Lambda workarounds
                            final int capturedArgumentCount = callbackInfoArgument;
                            this.remapMethodSelectorList(value, node, method, targets, (inferredDescriptor) -> {
                                if (inferredDescriptor.codePointAt(0) != '('
                                        || expectVoidFinal != (inferredDescriptor.codePointBefore(inferredDescriptor.length()) == 'V')) {
                                    return false;
                                }

                                Type[] inferredDescriptorArguments = Type.getArgumentTypes(inferredDescriptor);
                                if (inferredDescriptorArguments.length < capturedArgumentCount) {
                                    return false;
                                }

                                for (int j = 0; j < capturedArgumentCount; j++) {
                                    if (!arguments[j].getDescriptor().equals(inferredDescriptorArguments[j].getDescriptor())) {
                                        return false;
                                    }
                                }
                                return true;
                            });
                        } else {
                            this.logUnimplementedFeature("Unimplemented key in @Inject: " + name + " within node " + node.name);
                        }
                    }
                } else if (annot.desc.equals("Lorg/spongepowered/asm/mixin/injection/ModifyArg;")) {
                    if (mainAnnotation != null) {
                        throw new IllegalMixinException("Illegal mixin method " + node.name + "." + method.name + method.desc + ": The mixin handler is annotated with two or more incompatible annotations: " + mainAnnotation + " and " + annot.desc);
                    }
                    mainAnnotation = annot.desc;
                    for (int i = 0; i < annot.values.size(); i += 2) {
                        String name = (String) annot.values.get(i);
                        Object value = annot.values.get(i + 1);
                        if (name.equals("at")) {
                            this.remapAt(node.name, method.name + method.desc, -1, targets, (AnnotationNode) value);
                        } else if (name.equals("expect")
                                || name.equals("index")
                                || name.equals("require")) {
                            // Nothing to do
                        } else if (name.equals("method") || name.equals("target")) {
                            this.remapMethodSelectorList(value, node, method, targets, (inferredDescriptor) -> {
                                return inferredDescriptor.codePointAt(0) == '(';
                            });
                        } else {
                            this.logUnimplementedFeature("Unimplemented key in @Inject: " + name + " within node " + node.name);
                        }
                    }
                } else {
                    this.logUnimplementedFeature("Unknown mixin annotation on method " + node.name + "." + method.name + method.desc + ": " + annot.desc);
                }
            }
        }

        if (mainAnnotation == null) {
            this.handleOverwrite(null, targets, node, method);
        }
    }

    private void remapMethodSelectorList(Object selectors, @NotNull ClassNode originNode, MethodNode originMethod, @NotNull Collection<String> targets, @Nullable Predicate<@NotNull String> inferredDescriptorPredicate) throws IllegalMixinException, MissingFeatureException {
        @SuppressWarnings("unchecked")
        ListIterator<Object> it = ((List<Object>) selectors).listIterator();
        while (it.hasNext()) {
            int idx = it.nextIndex();
            Object o = it.next();

            if (o instanceof AnnotationNode) {
                this.remapDescAnnotation("Error while remapping @Desc selector in method " + originNode.name + "." + originMethod.name + originMethod.desc + ", index " + idx + ": ", targets, (AnnotationNode) o, false);
            } else {
                it.set((Object) this.remapTargetSelector("Error while remapping target selector in method " + originNode.name + "." + originMethod.name + originMethod.desc + ", index " + idx + ": ", (String) o, targets, inferredDescriptorPredicate));
            }
        }
    }

    @NotNull
    private String remapTargetSelector(@NotNull String errorPrefix, String targetSelector, @Nullable Collection<@NotNull String> targets, @Nullable Predicate<@NotNull String> inferredDescriptorPredicate) throws MissingFeatureException, IllegalMixinException {
        {
            StringBuilder purged = new StringBuilder();
            for (int i = 0; i < targetSelector.length(); i++) {
                int codepoint = targetSelector.codePointAt(i);
                if (!Character.isWhitespace(codepoint)) {
                    purged.appendCodePoint(codepoint);
                }
            }
            targetSelector = purged.toString();
        }

        int colonIndex = targetSelector.indexOf(':');
        int semicolonIndex = targetSelector.indexOf(';');
        int descStartIndex = targetSelector.indexOf('(');
        int endName;
        int startName;

        if (colonIndex >= 0) {
            if (descStartIndex >= 0) {
                throw new IllegalMixinException(errorPrefix + "The usage of the colon (':') indicates a field string, but the target selector contains a '(', which is an illegal character within field selectors.");
            }
            descStartIndex = colonIndex + 1;
        }

        @Nullable
        String owner;
        @Nullable
        String name;
        @Nullable
        String desc;

        if (semicolonIndex != -1 && (descStartIndex == -1 || semicolonIndex < descStartIndex)) {
            owner = targetSelector.substring(1, semicolonIndex);
            startName = semicolonIndex + 1;
        } else {
            owner = null;
            startName = 0;
        }
        if (descStartIndex == -1) {
            desc = null;
            endName = targetSelector.length();
        } else {
            desc = targetSelector.substring(descStartIndex);
            endName = descStartIndex;
            if (colonIndex >= 0) {
                endName--;
            }
        }
        if (endName > startName) {
            name = targetSelector.substring(startName, endName);
        } else {
            name = null;
        }

        inferMember:
        if (targets != null) {
            if (owner != null) {
                targets = Collections.singleton(owner);
                if (name != null && desc != null) {
                    break inferMember;
                }
            }

            String remappedOwner = null;
            boolean tornOwner = false;
            String remappedName = null;
            boolean tornName = false;
            String remappedDesc = null;
            boolean tornDesc = false;

            List<MemberRef> allReferences = new ArrayList<>();
            StringBuilder builder = new StringBuilder();
            for (String ownerType : targets) {
                Collection<MemberRef> references = this.lister.tryInferMember(ownerType, name, desc);
                for (MemberRef ref : references) {
                    if (inferredDescriptorPredicate != null && !inferredDescriptorPredicate.test(ref.getDesc())) {
                        continue;
                    } else {
                        allReferences.add(ref);
                    }
                    String memberOwner = this.lookup.getRemappedClassName(ref.getOwner());
                    String memberName;
                    String memberDesc;
                    if (ref.getDesc().codePointAt(0) == '(') {
                        memberName = this.lookup.getRemappedMethodName(ref.getOwner(), ref.getName(), ref.getDesc());
                        memberDesc = Remapper.getRemappedMethodDescriptor(this.lookup, ref.getDesc(), builder);
                    } else {
                        memberName = this.lookup.getRemappedFieldName(ref.getOwner(), ref.getName(), ref.getDesc());
                        memberDesc = Remapper.getRemappedFieldDescriptor(this.lookup, ref.getDesc(), builder);
                    }

                    if (remappedOwner == null) {
                        remappedOwner = memberOwner;
                    } else if (!remappedOwner.equals(memberOwner)) {
                        tornOwner = true;
                    }
                    if (remappedName == null) {
                        remappedName = memberName;
                    } else if (!remappedName.equals(memberName)) {
                        tornName = true;
                    }
                    if (remappedDesc == null) {
                        remappedDesc = memberDesc;
                    } else if (!remappedDesc.equals(memberDesc)) {
                        tornDesc = true;
                    }
                }
            }

            if (remappedDesc != null) {
                assert remappedName != null;
                assert remappedOwner != null;
                if (tornOwner || tornName || tornDesc) {
                    this.logUnimplementedFeature(errorPrefix + "The provided explicit target selector string is not fully qualified (that is the member either lacks a name, descriptor or owner or a combination thereof) and one of the missing components have torn mappings. Without the fully qualified member, the selector string cannot be adequately renamed as the actually targetted member is highly context-dependent. As such, this feature is not properly supported in micromixin-remapper. Potential ways of mitigating this issue involve: Implementing this feature yourself, using the fully qualified target selector or using @Desc (@Desc has more strongly defined behaviour when it comes to unspecified parts of the selector, but may not be recommended in most toolchains. However it's use is acceptable and even recommended within the stianloader toolchain - while minecraft-specific toolchains generally advise against the use of @Desc).\n\nList of all candidate references (for debugging purposes:)" + allReferences);
                }
                builder.setLength(0);
                builder.appendCodePoint('L').append(remappedOwner).appendCodePoint(';');
                builder.append(remappedName);
                builder.appendCodePoint(remappedDesc.codePointAt(0) != '(' ? ':' : ' ');
                builder.append(remappedDesc);
                return builder.toString();
            }
        }

        if (owner == null || name == null || desc == null) {
            this.logUnimplementedFeature(errorPrefix + "The provided explicit target selector string is not fully qualified (that is the member either lacks a name, descriptor or owner or a combination thereof). Without the fully qualified member, the selector string cannot be adequately renamed as the actually targetted member is highly context-dependent. As such, this feature is not supported in micromixin-remapper (but it is supported in micromixin-transformer and other mixin implementations!). Potential ways of mitigating this issue involve: Implementing this feature yourself, using the fully qualified target selector or using @Desc (@Desc has more strongly defined behaviour when it comes to unspecified parts of the selector, but may not be recommended in most toolchains. However it's use is acceptable and even recommended within the stianloader toolchain - while minecraft-specific toolchains generally advise against the use of @Desc).");
            return targetSelector;
        }

        StringBuilder remapped = new StringBuilder();
        remapped.appendCodePoint('L');
        remapped.append(this.lookup.getRemappedClassName(owner));
        remapped.appendCodePoint(';');
        if (desc.codePointAt(0) == '(') {
            remapped.appendCodePoint(' ').append(this.lookup.getRemappedMethodName(owner, name, desc));
        } else {
            remapped.appendCodePoint(':').append(this.lookup.getRemappedFieldName(owner, name, desc));
        }
        Remapper.remapSignature(this.lookup, desc, remapped);

        return remapped.toString();
    }
}
