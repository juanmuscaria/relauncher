// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.jvm;

/**
 * Callback for streaming download progress.
 */
public interface ProgressListener {
    /**
     * Called periodically during a download. {@code total} may be -1 if unknown.
     */
    void onProgress(long downloaded, long total);
}
