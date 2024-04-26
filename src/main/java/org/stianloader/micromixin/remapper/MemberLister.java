package org.stianloader.micromixin.remapper;

import java.util.Collection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.stianloader.remapper.MemberRef;

/**
 * The {@link MemberLister} presents a way of obtaining metadata about potential mixin target classes
 * that facilitate the remapping process. The main function of the {@link MemberLister} is to resolve
 * not fully qualified references to a fully qualified reference or to detect cases where the remapping
 * process would need to tear apart name hierarchies - this is especially prominent for overwrites - be
 * it explicit or implicit.
 *
 * <p>This interface does not define any behaviour on how the remapper needs to react to such mapping
 * difficulties. Instead, the interface provides the means of making a well-informed decision
 * that is more specific to the encountered scenario.
 */
public interface MemberLister {

    /**
     * Check whether a member with the given name and descriptor exists
     * with the class <code>clazz</code> or any of it's supertypes. Subtypes
     * should not be considered.
     *
     * <p>This method may also be called on fields. Whether a member is a field
     * or a method can quickly be validated by checking the first character of the descriptor:
     * If it is '(' it is a method, else it is a field.
     *
     * <p>This method is most critically used to detect mapping tears as well as to
     * handle implicit overwrites and the shadow annotation.
     *
     * @param clazz The owner to start searching in, in the source namespace.
     * @param name The name of the member, in the source namespace.
     * @param desc The descriptor of the member, in the source namespace.
     * @return True if the member was found in the hierarchy, false otherwise.
     */
    boolean hasMemberInHierarchy(@NotNull String clazz, @NotNull String name, @NotNull String desc);

    /**
     * Try to infer the members (stored as a {@link MemberRef member reference} in the source namespace)
     * of a class <code>owner</code> that match the given name or descriptor.
     * If name or descriptor is null, then the part should be considered unknown.
     *
     * <p>If multiple members match the criterions laid out by the method call, then implementors
     * should return all members that match within the returned {@link Collection}. Similarly, if no
     * members match the criterions, then an empty collection should be returned.
     *
     * <p>The {@link MemberRef} stored in the collection are in the source namespace, as are the 
     *
     * <p>This method is used for purposes of remapping string target selectors. That being said, at this
     * point in time it is not being used for mapping string target selectors within <code>&#64;At</code> annotations.
     * This is largely caused by the fact that the spongeian mixin specification explicitly states that the references
     * within <code>&#64;At</code>s should be full qualified or otherwise there may be issues at obfuscation time.
     *
     * <p>Implementors are not required to resolve the members within superclasses or elsewhere in the
     * inheritance hierarchy. In fact, callers might expect that {@link MemberRef#getOwner()}
     * equals to <code>owner</code> for all {@link MemberRef} instances returned by this method.
     *
     * @param owner The internal name of the owner of the members to collect.
     * @param name The name of the member within the source namespace.
     * @param desc The descriptor of the member.
     * @return A {@link Collection} of all {@link MemberRef member references} that match the criterions.
     */
    @NotNull
    Collection<MemberRef> tryInferMember(@NotNull String owner, @Nullable String name, @Nullable String desc);
}
