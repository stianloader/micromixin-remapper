package org.stianloader.micromixin.remapper.selectors;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.stianloader.micromixin.remapper.IllegalMixinException;
import org.stianloader.remapper.MappingLookup;

public interface AtSelector {
    boolean isMatchingFields();
    void remapArgs(@NotNull String errorPrefix, @Nullable List<String> args, @NotNull MappingLookup lookup) throws IllegalMixinException;
}
