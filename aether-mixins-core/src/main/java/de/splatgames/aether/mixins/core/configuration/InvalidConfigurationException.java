package de.splatgames.aether.mixins.core.configuration;

import java.io.Serial;

public class InvalidConfigurationException extends Exception {
    @Serial
    private static final long serialVersionUID = -5169505754350869689L;

    public InvalidConfigurationException() {
        super();
    }

    public InvalidConfigurationException(final String message) {
        super(message);
    }

    public InvalidConfigurationException(final Throwable cause) {
        super(cause);
    }

    public InvalidConfigurationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
