package com.juanmuscaria.relauncher.logger;

import java.util.logging.Level;
import java.util.logging.Logger;

public class JavaLoggerAdapter implements LoggerAdapter {
    private final Logger logger;

    public JavaLoggerAdapter(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message) {
        logger.log(Level.INFO, message);
    }

    @Override
    public void warn(String message) {
        logger.log(Level.WARNING, message);
    }

    @Override
    public void error(String message) {
        logger.log(Level.SEVERE, message);
    }

    @Override
    public void debug(String message) {
        logger.log(Level.FINE, message);
    }

    @Override
    public void warn(String message, Throwable t) {
        logger.log(Level.WARNING, message, t);
    }

    @Override
    public void error(String message, Throwable t) {
        logger.log(Level.SEVERE, message, t);
    }
}
