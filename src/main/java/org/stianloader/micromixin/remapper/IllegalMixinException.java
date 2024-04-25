package org.stianloader.micromixin.remapper;

import org.jetbrains.annotations.NotNull;

/**
 * An exception that is thrown whenever an aspect is encountered that would be illegal for any
 * mixin implementation. Examples include attempting to mixin into primitives or arrays,
 * broken descriptors or illegal characters in names, etc.
 *
 * <p>This exception is reserved for issues that are without a doubt fatal (or at the very best
 * unintended) at runtime
 */
@SuppressWarnings("serial")
public class IllegalMixinException extends Exception {

    /**
     * Constructor. Creates an {@link Exception} with the supplied detail message.
     *
     * @param detailMessage The detail message to pass to the superconstructor
     * @see Throwable#getMessage()
     */
    public IllegalMixinException(@NotNull String detailMessage) {
        super(detailMessage);
    }
}
