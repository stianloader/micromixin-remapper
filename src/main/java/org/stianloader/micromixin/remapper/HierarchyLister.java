package org.stianloader.micromixin.remapper;

import org.jetbrains.annotations.NotNull;

public interface HierarchyLister {
    boolean hasMemberInHierarchy(@NotNull String clazz, @NotNull String name, @NotNull String desc);
}
