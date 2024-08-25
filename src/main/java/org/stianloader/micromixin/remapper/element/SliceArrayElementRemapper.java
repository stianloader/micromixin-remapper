package org.stianloader.micromixin.remapper.element;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.AnnotationNode;
import org.stianloader.micromixin.remapper.IllegalMixinException;
import org.stianloader.micromixin.remapper.MissingFeatureException;
import org.stianloader.micromixin.remapper.RemapContext;

public class SliceArrayElementRemapper implements AnnotationElementRemapper<List<AnnotationNode>> {

    public static final SliceArrayElementRemapper INSTANCE = new SliceArrayElementRemapper();

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
