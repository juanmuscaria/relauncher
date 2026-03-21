// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.logger;

public interface LoggerAdapter {

    void info(String message);

    void warn(String message);

    void error(String message);

    void debug(String message);

    void warn(String message, Throwable t);

    void error(String message, Throwable t);
}
