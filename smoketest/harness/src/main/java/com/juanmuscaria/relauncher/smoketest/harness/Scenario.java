// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.smoketest.harness;

/**
 * Identifies a scenario within an area. Just two strings, the harness
 * enforces no schema on either beyond "non-empty, no path separators".
 */
public final class Scenario {
    private final String area;
    private final String name;

    public Scenario(String area, String name) {
        if (area == null || area.isEmpty() || area.indexOf('/') >= 0 || area.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("area must not contain path separators: " + area);
        }
        if (name == null || name.isEmpty() || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("scenario name must not contain path separators: " + name);
        }
        this.area = area;
        this.name = name;
    }

    public String area() {
        return area;
    }

    public String name() {
        return name;
    }
}
