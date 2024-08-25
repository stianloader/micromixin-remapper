package org.stianloader.micromixin.remapper.element;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.AnnotationNode;
import org.stianloader.micromixin.remapper.IllegalMixinException;
import org.stianloader.micromixin.remapper.MissingFeatureException;
import org.stianloader.micromixin.remapper.RemapContext;

public class SliceElementRemapper implements AnnotationElementRemapper<AnnotationNode> {

    public static final SliceElementRemapper INSTANCE = new SliceElementRemapper();

    @Override
    @NotNull
    public AnnotationNode remapNode(@NotNull RemapContext ctx, @NotNull AnnotationNode originalValue) throws MissingFeatureException, IllegalMixinException {
        ctx.remapper.remapSlice(ctx.mixinClassName, ctx.mixinMethod.name + ctx.mixinMethod.desc, -1, ctx.targets, originalValue);
        return originalValue;
    }
}
