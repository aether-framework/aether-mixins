package de.splatgames.aether.mixins.core.api;

public final class CallbackInfo {
    private boolean cancelled;

    public CallbackInfo() {
    }

    public void cancel() {
        this.cancelled = true;
    }

    public boolean isCancelled() {
        return this.cancelled;
    }
}
