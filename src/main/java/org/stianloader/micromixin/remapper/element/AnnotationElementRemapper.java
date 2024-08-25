package org.stianloader.micromixin.remapper.element;

import org.jetbrains.annotations.NotNull;
import org.stianloader.micromixin.remapper.IllegalMixinException;
import org.stianloader.micromixin.remapper.MissingFeatureException;
import org.stianloader.micromixin.remapper.RemapContext;

public interface AnnotationElementRemapper<T> {
    @NotNull
    T remapNode(@NotNull RemapContext ctx, @NotNull T originalValue) throws MissingFeatureException, IllegalMixinException;
}
