package org.stianloader.micromixin.remapper.selectors;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.stianloader.micromixin.remapper.IllegalMixinException;
import org.stianloader.remapper.MappingLookup;

public class ReturnSelector implements AtSelector {

    @NotNull
    public static final ReturnSelector INSTANCE = new ReturnSelector();

    private ReturnSelector() {
        // Reduced constructor visibility (use the singleton field instead)
    }

    @Override
    public boolean isMatchingFields() {
        return false;
    }

    @Override
    public void remapArgs(@NotNull String errorPrefix, @Nullable List<String> args, @NotNull MappingLookup lookup) throws IllegalMixinException {
        if (args != null) {
            throw new IllegalMixinException(errorPrefix + "The RETURN @At injection point selector does not expect an args argument, but the redundant discriminator was specified. Following approaches towards resolving the issue exist:\n"
                    + "\t1. Drop the 'args' element in the @At annotation.\n"
                    + "\t2. Report this as a bug to micromixin-remapper and micromixin-transformer, and attach why you would expect for the behaviour to differ from the current implementation.");
        }
    }
}
