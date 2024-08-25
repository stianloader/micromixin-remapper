package org.stianloader.micromixin.remapper.element;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.AnnotationNode;
import org.stianloader.micromixin.remapper.IllegalMixinException;
import org.stianloader.micromixin.remapper.MissingFeatureException;
import org.stianloader.micromixin.remapper.RemapContext;

public class AtArrayElementRemapper implements AnnotationElementRemapper<List<AnnotationNode>> {

    public static final AtArrayElementRemapper INSTANCE = new AtArrayElementRemapper();

    @Override
    @NotNull
    public List<AnnotationNode> remapNode(@NotNull RemapContext ctx, @NotNull List<AnnotationNode> originalValue) throws MissingFeatureException, IllegalMixinException {
        ctx.remapper.remapAtArray(ctx.mixinClassName, ctx.mixinMethod.name + ctx.mixinMethod.desc, ctx.targets, originalValue);
        return originalValue;
    }
}
