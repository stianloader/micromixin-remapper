package org.stianloader.micromixin.remapper.element;

import org.jetbrains.annotations.NotNull;
import org.stianloader.micromixin.remapper.RemapContext;

public class NopElementRemapper implements AnnotationElementRemapper<Object> {

    public static final NopElementRemapper INSTANCE = new NopElementRemapper();

    @Override
    @NotNull
    public Object remapNode(@NotNull RemapContext ctx, @NotNull Object originalValue) {
        return originalValue;
    }
}
