// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher;

/**
 * Describes why a relaunch didn't happen.
 * <p>
 * You'll only ever see one of these if something went wrong or the relaunch
 * was intentionally skipped - on success the JVM is replaced (or halted)
 * before this object could be returned.
 */
public final class RelaunchResult {

    private final Status status;
    private final String reason;
    private final Throwable cause;

    private RelaunchResult(Status status, String reason, Throwable cause) {
        this.status = status;
        this.reason = reason;
        this.cause = cause;
    }

    /**
     * The relaunch was intentionally skipped (e.g. unsupported launcher).
     */
    public static RelaunchResult skipped(String reason) {
        return new RelaunchResult(Status.SKIPPED, reason, null);
    }

    /**
     * The relaunch was attempted but failed.
     */
    public static RelaunchResult failed(String reason, Throwable cause) {
        return new RelaunchResult(Status.FAILED, reason, cause);
    }

    /**
     * The relaunch was attempted but failed (no underlying exception).
     */
    public static RelaunchResult failed(String reason) {
        return new RelaunchResult(Status.FAILED, reason, null);
    }

    public Status status() {
        return status;
    }

    public String reason() {
        return reason;
    }

    /**
     * The underlying exception, if there is one.
     */
    public Throwable cause() {
        return cause;
    }

    public boolean isSkipped() {
        return status == Status.SKIPPED;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }

    @Override
    public String toString() {
        var sb = new StringBuilder(status.name()).append(": ").append(reason);
        if (cause != null) {
            sb.append(" (").append(cause).append(")");
        }
        return sb.toString();
    }

    public enum Status {
        SKIPPED,
        FAILED
    }
}
