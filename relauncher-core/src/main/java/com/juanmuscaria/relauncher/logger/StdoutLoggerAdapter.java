package com.juanmuscaria.relauncher.logger;

public class StdoutLoggerAdapter implements LoggerAdapter {
    private final String name;

    public StdoutLoggerAdapter(String name) {
        this.name = name;
    }

    @Override
    public void info(String message) {
        System.out.println("[" + name + "] " + message);
    }

    @Override
    public void warn(String message) {
        System.out.println("[" + name + "] WARN: " + message);
    }

    @Override
    public void error(String message) {
        System.err.println("[" + name + "] ERROR: " + message);
    }

    @Override
    public void debug(String message) {
        if (Boolean.getBoolean("relauncher.debug")) {
            System.out.println("[" + name + "] DEBUG: " + message);
        }
    }

    @Override
    public void warn(String message, Throwable t) {
        warn(message);
        t.printStackTrace(System.out);
    }

    @Override
    public void error(String message, Throwable t) {
        error(message);
        t.printStackTrace(System.err);
    }
}
