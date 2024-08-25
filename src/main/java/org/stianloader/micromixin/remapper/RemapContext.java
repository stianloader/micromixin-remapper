package org.stianloader.micromixin.remapper;

import java.util.Collection;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.MethodNode;

public final class RemapContext {
    @NotNull
    public final String mixinClassName;
    @NotNull
    public final MethodNode mixinMethod;
    @NotNull
    public final MicromixinRemapper remapper;
    @NotNull
    public final Collection<@NotNull String> targets;

    public RemapContext(@NotNull MicromixinRemapper remapper, @NotNull String mixinClassName,
            @NotNull MethodNode mixinMethod, @NotNull Collection<@NotNull String> targets) {
        this.remapper = remapper;
        this.mixinClassName = mixinClassName;
        this.mixinMethod = mixinMethod;
        this.targets = targets;
    }
}
