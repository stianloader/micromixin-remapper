package org.stianloader.micromixin.remapper;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.AnnotationNode;

/**
 * Exception that can be thrown inside {@link MicromixinRemapper#logUnimplementedFeature(String)}
 * to notify that a feature is missing in the remapper. Most notable causes include an {@link AnnotationNode}
 * having a value entry the remapper does not recognise or the remapper detecting a mixin annotation it does not
 * know how to remap. More often than not, such missing features would not impact micromixin-remapper's capabilities
 * to remap mixins, but the remapper is written to be fail-fast in order to quickly detect unexpected uses
 * of the remapper infrastructure.
 */
@SuppressWarnings("serial")
public class MissingFeatureException extends Exception {
    /**
     * Constructor. Creates an {@link Exception} with the supplied detail message.
     *
     * @param description The detail message to pass to the superconstructor
     * @see Throwable#getMessage()
     */
    public MissingFeatureException(@NotNull String description) {
        super(description);
    }
}
