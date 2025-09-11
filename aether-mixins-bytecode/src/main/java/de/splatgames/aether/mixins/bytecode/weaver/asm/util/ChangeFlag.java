package de.splatgames.aether.mixins.bytecode.weaver.asm.util;

/**
 * A mutable flag used to track whether a bytecode modification has occurred
 * during the weaving process.
 *
 * <p>This class acts as a simple state holder that can be toggled by different
 * components, such as ASM visitors, to signal that a change has been applied.
 * It is primarily used by the weaving pipeline to decide whether the resulting
 * byte array should be emitted or skipped.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ChangeFlag flag = new ChangeFlag();
 *
 * if (!flag.getAndSet()) {
 *     // First time a change occurred
 *     System.out.println("Modification applied!");
 * }
 *
 * if (flag.isSet()) {
 *     // There has been at least one modification
 *     System.out.println("Class was modified.");
 * }
 * }</pre>
 *
 * <p>Instances of this class are not thread-safe and must be confined to a single
 * weaving operation or synchronized externally if shared.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class ChangeFlag {

    /**
     * Indicates whether a change has occurred.
     */
    private boolean value = false;

    /**
     * Marks this flag as set and returns the previous state in one atomic operation.
     *
     * <p>This method is typically used when you need to both check if a change has
     * already been applied and mark that a new change is now applied.</p>
     *
     * @return {@code true} if the flag was already set before this call,
     * {@code false} if it was clear and has now been set.
     */
    public boolean getAndSet() {
        final boolean old = this.value;
        this.value = true;
        return old;
    }

    /**
     * Checks whether this flag has been set.
     *
     * @return {@code true} if a change has occurred at any point,
     * {@code false} if no changes have been applied yet.
     */
    public boolean isSet() {
        return this.value;
    }
}
