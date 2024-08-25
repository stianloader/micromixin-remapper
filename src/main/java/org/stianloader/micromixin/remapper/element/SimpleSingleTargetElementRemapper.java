package org.stianloader.micromixin.remapper.element;

import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.stianloader.micromixin.remapper.IllegalMixinException;
import org.stianloader.micromixin.remapper.MissingFeatureException;
import org.stianloader.micromixin.remapper.RemapContext;

public class SimpleSingleTargetElementRemapper implements AnnotationElementRemapper<Object> {

    public static final SimpleSingleTargetElementRemapper INSTANCE = new SimpleSingleTargetElementRemapper();

    @Override
    @NotNull
    public Object remapNode(@NotNull RemapContext ctx, @NotNull Object originalValue) throws MissingFeatureException, IllegalMixinException {
        List<@NotNull Object> wrappedValue = Collections.singletonList(originalValue);
        ctx.remapper.remapMethodSelectorList(wrappedValue, ctx.mixinClassName, ctx.mixinMethod, ctx.targets, (inferredDescriptor) -> {
            return inferredDescriptor.codePointAt(0) == '(';
        });
        return wrappedValue.get(0);
    }
}
