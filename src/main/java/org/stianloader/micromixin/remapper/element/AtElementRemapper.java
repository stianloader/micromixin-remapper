package org.stianloader.micromixin.remapper.element;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.AnnotationNode;
import org.stianloader.micromixin.remapper.IllegalMixinException;
import org.stianloader.micromixin.remapper.MissingFeatureException;
import org.stianloader.micromixin.remapper.RemapContext;

public class AtElementRemapper implements AnnotationElementRemapper<AnnotationNode> {

    public static final AtElementRemapper INSTANCE = new AtElementRemapper();

    @Override
    @NotNull
    public AnnotationNode remapNode(@NotNull RemapContext ctx, @NotNull AnnotationNode originalValue) throws MissingFeatureException, IllegalMixinException {
        ctx.remapper.remapAt(ctx.mixinClassName, ctx.mixinMethod.name + ctx.mixinMethod.desc, -1, ctx.targets, originalValue);
        return originalValue;
    }
}
