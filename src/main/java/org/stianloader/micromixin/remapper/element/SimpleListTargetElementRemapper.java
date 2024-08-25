package org.stianloader.micromixin.remapper.element;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.stianloader.micromixin.remapper.IllegalMixinException;
import org.stianloader.micromixin.remapper.MissingFeatureException;
import org.stianloader.micromixin.remapper.RemapContext;

public class SimpleListTargetElementRemapper implements AnnotationElementRemapper<List<?>> {

    public static final SimpleListTargetElementRemapper INSTANCE = new SimpleListTargetElementRemapper();

    @Override
    @NotNull
    public List<?> remapNode(@NotNull RemapContext ctx, @NotNull List<?> originalValue) throws MissingFeatureException, IllegalMixinException {
        ctx.remapper.remapMethodSelectorList(originalValue, ctx.mixinClassName, ctx.mixinMethod, ctx.targets, (inferredDescriptor) -> {
            return inferredDescriptor.codePointAt(0) == '(';
        });
        return originalValue;
    }
}
