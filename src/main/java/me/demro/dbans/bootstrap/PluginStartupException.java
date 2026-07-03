package me.demro.dbans.bootstrap;

public final class PluginStartupException extends RuntimeException {

    public PluginStartupException(String message) {
        super(message);
    }

    public PluginStartupException(String message, Throwable cause) {
        super(message, cause);
    }
}
