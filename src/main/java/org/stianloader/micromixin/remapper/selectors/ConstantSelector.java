package org.stianloader.micromixin.remapper.selectors;

import java.util.List;
import java.util.ListIterator;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.stianloader.micromixin.remapper.IllegalMixinException;
import org.stianloader.remapper.MappingLookup;

public class ConstantSelector implements AtSelector {

    @NotNull
    public static final ConstantSelector INSTANCE = new ConstantSelector();

    private ConstantSelector() {
        // Reduced constructor visibility (use the singleton field instead)
    }

    @Override
    public boolean isMatchingFields() {
        return false;
    }

    @Override
    public void remapArgs(@NotNull String errorPrefix, @Nullable List<String> args, @NotNull MappingLookup lookup) throws IllegalMixinException {
        if (args == null) {
            throw new IllegalMixinException(errorPrefix + "The CONSTANT @At injection point selector requires an args argument, but the discriminator was not specified. Following approaches towards resolving the issue exist:\n"
                    + "\t1. Specify the 'args' element in the @At annotation.\n"
                    + "\t2. Report this as a bug to micromixin-remapper and micromixin-transformer, and attach why you would expect for the behaviour to differ from the current implementation.");
        }

        boolean matchedOne = false;
        ListIterator<String> itr = args.listIterator();
        while (itr.hasNext()) {
            String s = itr.next();
            int equalIndex = s.indexOf('=');
            if (equalIndex == -1) {
                continue;
            }
            if (matchedOne) {
                throw new IllegalMixinException(errorPrefix + "The CONSTANT @At injection point selector requires a single args argument, but the discriminator specifies multiple of them. Following approaches towards resolving the issue exist:\n"
                        + "\t1. Specify a single 'args' element in the @At annotation (or rather said define it as an array with a single element).\n"
                        + "\t2. Report this as a bug to micromixin-remapper and micromixin-transformer, and attach why you would expect for the behaviour to differ from the current implementation.");
            }
            matchedOne = true;
            String key = s.substring(0, equalIndex);
            String value = s.substring(equalIndex + 1);
            if (key.equals("floatValue")
                    || key.equals("nullValue")
                    || key.equals("intValue")
                    || key.equals("stringValue")
                    || key.equals("doubleValue")
                    || key.equals("longValue")) {
                continue;
            } else if (key.equals("classValue")) {
                if (value.isEmpty()) {
                    continue;
                }
                if (value.indexOf('.') != -1) {
                    throw new IllegalMixinException(errorPrefix + "Illegal class descriptor for CONSTANT @At injection point argument. A class descriptor is formatted as follows: L<internalName>;. Note that internal name packages are separated using forward slashes ('/') and not dots ('.')");
                }
                if (value.codePointBefore(value.length()) == ';') {
                    if (value.codePointAt(0) != 'L') {
                        throw new IllegalMixinException(errorPrefix + "Illegal class descriptor for CONSTANT @At injection point argument. A class descriptor is formatted as follows: L<internalName>;.");
                    }
                    String mapped = lookup.getRemappedClassNameFast(value.substring(1, value.length() - 1));
                    if (mapped != null) {
                        itr.set('L' + mapped + ';');
                    }
                } else {
                    itr.set(lookup.getRemappedClassName(value));
                }
            } else {
                throw new IllegalMixinException(errorPrefix + "Unknown CONSTANT @At injection point key: '" + key + "'. Note: Whitespaces are not allowed between either side of the equals.");
            }
        }

        if (!matchedOne) {
            throw new IllegalMixinException(errorPrefix + "Cannot find any constant values in @At(\"CONSTANT\") args. An example would be @At(value = \"CONSTANT\", args = {\"intValue=5\"}). Note: Whitespaces are not allowed between either side of the equals.");
        }
    }
}
