package org.stianloader.micromixin.remapper;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;

final class AnnotationRemapper {
    static interface AnnotationElementRemapper<T> {
        @NotNull
        T remapNode(@NotNull RemapContext ctx, @NotNull T originalValue) throws MissingFeatureException, IllegalMixinException;
    }

    static class AtArrayElementRemapper implements AnnotationElementRemapper<List<AnnotationNode>> {

        private static final AtArrayElementRemapper INSTANCE = new AtArrayElementRemapper();

        @Override
        @NotNull
        public List<AnnotationNode> remapNode(@NotNull RemapContext ctx, @NotNull List<AnnotationNode> originalValue) throws MissingFeatureException, IllegalMixinException {
            ctx.remapper.remapAtArray(ctx.mixinClassName, ctx.mixinMethod.name + ctx.mixinMethod.desc, ctx.targets, originalValue);
            return originalValue;
        }
    }

    static class AtElementRemapper implements AnnotationElementRemapper<AnnotationNode> {

        private static final AtElementRemapper INSTANCE = new AtElementRemapper();

        @Override
        @NotNull
        public AnnotationNode remapNode(@NotNull RemapContext ctx, @NotNull AnnotationNode originalValue) throws MissingFeatureException, IllegalMixinException {
            ctx.remapper.remapAt(ctx.mixinClassName, ctx.mixinMethod.name + ctx.mixinMethod.desc, -1, ctx.targets, originalValue);
            return originalValue;
        }
    }

    static class InjectListTargetElementRemapper implements AnnotationElementRemapper<List<?>> {

        private static final InjectListTargetElementRemapper INSTANCE = new InjectListTargetElementRemapper();

        @Override
        @NotNull
        public List<?> remapNode(@NotNull RemapContext ctx, @NotNull List<?> originalValue) throws MissingFeatureException, IllegalMixinException {
            Type[] arguments = Type.getArgumentTypes(ctx.mixinMethod.desc);
            boolean expectVoid = false;
            boolean foundCallbackInfo = false;
            int callbackInfoArgument;
            for (callbackInfoArgument = 0; callbackInfoArgument < arguments.length; callbackInfoArgument++) {
                Type arg = arguments[callbackInfoArgument];
                String descriptor = arg.getDescriptor();
                if ((expectVoid = descriptor.equals("L" + MicromixinRemapper.CALLBACK_INFO_CLASS + ";")) || descriptor.equals("L" + MicromixinRemapper.CALLBACK_INFO_RETURNABLE_CLASS + ";")) {
                    foundCallbackInfo = true;
                    break;
                }
            }

            if (!foundCallbackInfo) {
                throw new IllegalMixinException("Annotated method " + ctx.mixinClassName + "." + ctx.mixinMethod.name + ctx.mixinMethod.desc + " lacks type argument " + MicromixinRemapper.CALLBACK_INFO_CLASS + " or " + MicromixinRemapper.CALLBACK_INFO_RETURNABLE_CLASS);
            }

            final boolean expectVoidFinal = expectVoid; // Lambda workarounds
            final int capturedArgumentCount = callbackInfoArgument;

            ctx.remapper.remapMethodSelectorList(originalValue, ctx.mixinClassName, ctx.mixinMethod, ctx.targets, (inferredDescriptor) -> {
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
            return originalValue;
        }
    }

    static class NopElementRemapper implements AnnotationElementRemapper<Object> {

        private static final NopElementRemapper INSTANCE = new NopElementRemapper();

        @Override
        @NotNull
        public Object remapNode(@NotNull RemapContext ctx, @NotNull Object originalValue) {
            return originalValue;
        }
    }

    static final class RemapContext {
        @NotNull
        private final String mixinClassName;
        @NotNull
        private final MethodNode mixinMethod;
        @NotNull
        private final MicromixinRemapper remapper;
        @NotNull
        private final Collection<@NotNull String> targets;

        public RemapContext(@NotNull MicromixinRemapper remapper, @NotNull String mixinClassName,
                @NotNull MethodNode mixinMethod, @NotNull Collection<@NotNull String> targets) {
            this.remapper = remapper;
            this.mixinClassName = mixinClassName;
            this.mixinMethod = mixinMethod;
            this.targets = targets;
        }
    }

    static class SimpleListTargetElementRemapper implements AnnotationElementRemapper<List<?>> {

        private static final SimpleListTargetElementRemapper INSTANCE = new SimpleListTargetElementRemapper();

        @Override
        @NotNull
        public List<?> remapNode(@NotNull RemapContext ctx, @NotNull List<?> originalValue) throws MissingFeatureException, IllegalMixinException {
            ctx.remapper.remapMethodSelectorList(originalValue, ctx.mixinClassName, ctx.mixinMethod, ctx.targets, (inferredDescriptor) -> {
                return inferredDescriptor.codePointAt(0) == '(';
            });
            return originalValue;
        }
    }

    static class SliceArrayElementRemapper implements AnnotationElementRemapper<List<AnnotationNode>> {

        private static final SliceArrayElementRemapper INSTANCE = new SliceArrayElementRemapper();

        @Override
        @NotNull
        public List<AnnotationNode> remapNode(@NotNull RemapContext ctx, @NotNull List<AnnotationNode> originalValue) throws MissingFeatureException, IllegalMixinException {
            int ordinal = 0;
            for (AnnotationNode slice : originalValue) {
                ctx.remapper.remapSlice(ctx.mixinClassName, ctx.mixinMethod.name + ctx.mixinMethod.desc, ordinal++, ctx.targets, slice);
            }
            return originalValue;
        }
    }

    static class SliceElementRemapper implements AnnotationElementRemapper<AnnotationNode> {

        private static final SliceElementRemapper INSTANCE = new SliceElementRemapper();

        @Override
        @NotNull
        public AnnotationNode remapNode(@NotNull RemapContext ctx, @NotNull AnnotationNode originalValue) throws MissingFeatureException, IllegalMixinException {
            ctx.remapper.remapSlice(ctx.mixinClassName, ctx.mixinMethod.name + ctx.mixinMethod.desc, -1, ctx.targets, originalValue);
            return originalValue;
        }
    }

    static final Map<String, AnnotationRemapper> ANNOTATION_REMAPPERS = new HashMap<>();

    static {
        AnnotationRemapper.ANNOTATION_REMAPPERS.put(
                "Lorg/spongepowered/asm/mixin/injection/Inject;",
                new AnnotationRemapper()
                    .withFrequencyBounds()
                    .withUnchangedElement("cancellable")
                    .withUnchangedElement("locals")
                    .withListAt()
                    .withListSlice()
                    .withInjectListMethodTarget()
        );

        AnnotationRemapper.ANNOTATION_REMAPPERS.put(
                "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
                new AnnotationRemapper()
                    .withFrequencyBounds()
                    .withIndex()
                    .withSingularAt()
                    .withSingularSlice()
                    .withSimpleListMethodTarget()
        );

        AnnotationRemapper.ANNOTATION_REMAPPERS.put(
                "Lorg/spongepowered/asm/mixin/injection/Redirect;",
                new AnnotationRemapper()
                    .withFrequencyBounds()
                    .withSingularAt()
                    .withSingularSlice()
                    .withSimpleListMethodTarget()
        );

        AnnotationRemapper.ANNOTATION_REMAPPERS.put(
                "Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;",
                new AnnotationRemapper()
                    .withFrequencyBounds()
                    .withListAt()
                    .withListSlice()
                    .withSimpleListMethodTarget()
        );
    }

    private final Map<String, AnnotationElementRemapper<?>> elementRemappers = new HashMap<>();

    public final void remapAnnotation(@NotNull RemapContext ctx, @NotNull AnnotationNode annotation) throws MissingFeatureException, IllegalMixinException {
        int i = annotation.values.size();
        while (i-- != 0) {
            Object elementValue = annotation.values.get(i--);
            String elementName = (String) annotation.values.get(i);
            @SuppressWarnings("unchecked")
            AnnotationElementRemapper<Object> remapper = (AnnotationElementRemapper<Object>) this.elementRemappers.get(elementName);
            if (remapper == null) {
                throw new IllegalStateException("Remapper does not know how to remap element '" + elementName + "' of annotation '" + annotation.desc + "'");
            } else if (elementValue == null) {
                continue; // ???
            }
            annotation.values.set(i + 1, remapper.remapNode(ctx, elementValue));
        }
    }

    public AnnotationRemapper withFrequencyBounds() {
        return this.withUnchangedElement("expect")
                .withUnchangedElement("allow")
                .withUnchangedElement("require");
    }

    public AnnotationRemapper withIndex() {
        return this.withUnchangedElement("index");
    }

    public AnnotationRemapper withInjectListMethodTarget() {
        this.elementRemappers.put("method", InjectListTargetElementRemapper.INSTANCE);
        this.elementRemappers.put("target", InjectListTargetElementRemapper.INSTANCE);
        return this;
    }

    public AnnotationRemapper withListAt() {
        this.elementRemappers.put("at", AtArrayElementRemapper.INSTANCE);
        return this;
    }

    public AnnotationRemapper withListSlice() {
        this.elementRemappers.put("slice", SliceArrayElementRemapper.INSTANCE);
        return this;
    }

    public AnnotationRemapper withSimpleListMethodTarget() {
        this.elementRemappers.put("method", SimpleListTargetElementRemapper.INSTANCE);
        this.elementRemappers.put("target", SimpleListTargetElementRemapper.INSTANCE);
        return this;
    }

    public AnnotationRemapper withSingularAt() {
        this.elementRemappers.put("at", AtElementRemapper.INSTANCE);
        return this;
    }

    public AnnotationRemapper withSingularSlice() {
        this.elementRemappers.put("slice", SliceElementRemapper.INSTANCE);
        return this;
    }

    public AnnotationRemapper withUnchangedElement(@NotNull String elementName) {
        this.elementRemappers.put(elementName, NopElementRemapper.INSTANCE);
        return this;
    }
}
