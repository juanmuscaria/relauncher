// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.jvm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Fallback probe: spawns {@code java -XshowSettings:properties -version} and
 * parses {@code java.specification.version}, {@code java.vendor}, and
 * {@code os.arch} from stderr.
 */
@SuppressWarnings("CharsetObjectCanBeUsed")
public final class ProcessProbe {

    private static final long TIMEOUT_SECONDS = 5;

    private ProcessProbe() {
    }

    /**
     * Spawn and parse. Returns an empty {@link Result} on any error or timeout.
     */
    public static Result probe(Path executable) {
        try {
            var p = new ProcessBuilder(
                executable.toString(), "-XshowSettings:properties", "-version")
                .redirectErrorStream(false)
                .start();

            var out = new ByteArrayOutputStream();
            drain(p.getErrorStream(), out);
            drain(p.getInputStream(), new OutputStream() {
                @Override
                public void write(int b) {

                }
            }); // discard stdout

            if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return new Result();
            }

            return parseStderr(out.toString(StandardCharsets.UTF_8.name()));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new Result();
        }
    }

    private static void drain(InputStream in, OutputStream sink) throws IOException {
        var buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            sink.write(buf, 0, n);
        }
    }

    static Result parseStderr(String stderr) {
        var r = new Result();
        if (stderr == null) return r;

        String specVersion = null;
        for (var line : stderr.split("\\R")) {
            var trimmed = line.trim();
            var eq = trimmed.indexOf('=');
            if (eq <= 0) continue;
            var key = trimmed.substring(0, eq).trim();
            var value = trimmed.substring(eq + 1).trim();
            switch (key) {
                case "java.version":
                    r.javaVersion = value;
                    break;
                case "java.specification.version":
                    specVersion = value;
                    break;
                case "java.vendor":
                    r.implementor = value;
                    break;
                case "os.arch":
                    r.osArch = value;
                    break;
                default: // ignore
            }
        }
        if (r.javaVersion == null) {
            r.javaVersion = specVersion;
        }
        return r;
    }

    /**
     * Mirror {@link ReleaseFileProbe.Result} field shape for easy merging.
     */
    public static final class Result {
        public String javaVersion;
        public String implementor;
        public String osArch;

        public boolean isEmpty() {
            return javaVersion == null && implementor == null && osArch == null;
        }
    }
}
