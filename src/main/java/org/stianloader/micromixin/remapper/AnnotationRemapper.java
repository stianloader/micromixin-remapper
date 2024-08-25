package org.stianloader.micromixin.remapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.AnnotationNode;
import org.stianloader.micromixin.remapper.element.AnnotationElementRemapper;
import org.stianloader.micromixin.remapper.element.AtArrayElementRemapper;
import org.stianloader.micromixin.remapper.element.AtElementRemapper;
import org.stianloader.micromixin.remapper.element.ImplicitTargetElementResolver;
import org.stianloader.micromixin.remapper.element.InjectListTargetElementRemapper;
import org.stianloader.micromixin.remapper.element.NopElementRemapper;
import org.stianloader.micromixin.remapper.element.SimpleListTargetElementRemapper;
import org.stianloader.micromixin.remapper.element.SimpleSingleTargetElementRemapper;
import org.stianloader.micromixin.remapper.element.SliceArrayElementRemapper;
import org.stianloader.micromixin.remapper.element.SliceElementRemapper;

final class AnnotationRemapper {
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

        AnnotationRemapper.ANNOTATION_REMAPPERS.put(
                "Lorg/stianloader/micromixin/annotations/CanonicalOverwrite;",
                new AnnotationRemapper()
                    .runBefore(ImplicitTargetElementResolver.INSTANCE)
                    .withSimpleSingleMethodTarget()
        );
    }

    @NotNull
    private final Map<String, AnnotationElementRemapper<?>> elementRemappers = new HashMap<>();
    @NotNull
    private final List<@NotNull BiConsumer<@NotNull RemapContext, @NotNull AnnotationNode>> runBefores = new ArrayList<>();

    public final void remapAnnotation(@NotNull RemapContext ctx, @NotNull AnnotationNode annotation) throws MissingFeatureException, IllegalMixinException {
        for (BiConsumer<@NotNull RemapContext, @NotNull AnnotationNode> runBefore : this.runBefores) {
            runBefore.accept(ctx, annotation);
        }

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

    @NotNull
    @Contract(pure = false, mutates = "this", value = "null -> fail; !null -> this")
    public AnnotationRemapper runBefore(@NotNull BiConsumer<@NotNull RemapContext, @NotNull AnnotationNode> runBefore) {
        this.runBefores.add(Objects.requireNonNull(runBefore, "Supplied argument 'runBefore' may not be null."));
        return this;
    }

    @NotNull
    @Contract(pure = false, mutates = "this", value = "-> this")
    public AnnotationRemapper withFrequencyBounds() {
        return this.withUnchangedElement("expect")
                .withUnchangedElement("allow")
                .withUnchangedElement("require");
    }

    @NotNull
    @Contract(pure = false, mutates = "this", value = "-> this")
    public AnnotationRemapper withIndex() {
        return this.withUnchangedElement("index");
    }

    @NotNull
    @Contract(pure = false, mutates = "this", value = "-> this")
    public AnnotationRemapper withInjectListMethodTarget() {
        this.elementRemappers.put("method", InjectListTargetElementRemapper.INSTANCE);
        this.elementRemappers.put("target", InjectListTargetElementRemapper.INSTANCE);
        return this;
    }

    @NotNull
    @Contract(pure = false, mutates = "this", value = "-> this")
    public AnnotationRemapper withListAt() {
        this.elementRemappers.put("at", AtArrayElementRemapper.INSTANCE);
        return this;
    }

    @NotNull
    @Contract(pure = false, mutates = "this", value = "-> this")
    public AnnotationRemapper withListSlice() {
        this.elementRemappers.put("slice", SliceArrayElementRemapper.INSTANCE);
        return this;
    }

    @NotNull
    @Contract(pure = false, mutates = "this", value = "-> this")
    public AnnotationRemapper withSimpleListMethodTarget() {
        this.elementRemappers.put("method", SimpleListTargetElementRemapper.INSTANCE);
        this.elementRemappers.put("target", SimpleListTargetElementRemapper.INSTANCE);
        return this;
    }

    @NotNull
    @Contract(pure = false, mutates = "this", value = "-> this")
    public AnnotationRemapper withSimpleSingleMethodTarget() {
        this.elementRemappers.put("method", SimpleSingleTargetElementRemapper.INSTANCE);
        this.elementRemappers.put("target", SimpleSingleTargetElementRemapper.INSTANCE);
        return this;
    }

    @NotNull
    @Contract(pure = false, mutates = "this", value = "-> this")
    public AnnotationRemapper withSingularAt() {
        this.elementRemappers.put("at", AtElementRemapper.INSTANCE);
        return this;
    }

    @NotNull
    @Contract(pure = false, mutates = "this", value = "-> this")
    public AnnotationRemapper withSingularSlice() {
        this.elementRemappers.put("slice", SliceElementRemapper.INSTANCE);
        return this;
    }

    @NotNull
    @Contract(pure = false, mutates = "this", value = "-> this")
    public AnnotationRemapper withUnchangedElement(@NotNull String elementName) {
        this.elementRemappers.put(elementName, NopElementRemapper.INSTANCE);
        return this;
    }
}
