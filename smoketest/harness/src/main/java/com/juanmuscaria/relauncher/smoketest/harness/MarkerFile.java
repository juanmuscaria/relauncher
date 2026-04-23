// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

package com.juanmuscaria.relauncher.smoketest.harness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Hand-rolled JSON for marker files, schema is flat enough that a tiny
 * writer + reader beats pulling a JSON lib into relauncher-core
 */
public final class MarkerFile {
    private MarkerFile() {
    }

    public static Path resolve(Path baseDir, Scenario s) {
        return baseDir.resolve(s.area()).resolve(s.name() + ".json");
    }

    // atomic write, serialize to .tmp then rename
    public static void write(Path path, ScenarioResult result) throws IOException {
        Files.createDirectories(path.getParent());
        var tmp = path.resolveSibling(path.getFileName() + ".tmp");

        var sb = new StringBuilder(512);
        sb.append("{\n");
        appendString(sb, "  ", "area", result.scenario().area()).append(",\n");
        appendString(sb, "  ", "scenario", result.scenario().name()).append(",\n");
        appendString(sb, "  ", "outcome", result.outcome().name().toLowerCase(Locale.ROOT)).append(",\n");
        appendString(sb, "  ", "reason", result.reason()).append(",\n");
        sb.append("  \"captured\": ");
        appendMap(sb, result.captured(), "  ");
        sb.append(",\n");
        sb.append("  \"durationMs\": ").append(result.durationMs()).append(",\n");
        appendString(sb, "  ", "timestamp", Instant.now().toString()).append("\n");
        sb.append("}\n");

        Files.write(tmp, sb.toString().getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // returns area, scenario, outcome, reason as strings, captures are ignored
    public static Map<String, String> readSummary(Path path) throws IOException {
        var text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        Map<String, String> out = new LinkedHashMap<>();
        for (var key : new String[]{"area", "scenario", "outcome", "reason"}) {
            var v = extractString(text, key);
            if (v != null) out.put(key, v);
        }
        return out;
    }

    private static StringBuilder appendString(StringBuilder sb, String indent, String key, String value) {
        sb.append(indent).append('"').append(key).append("\": \"").append(escape(value)).append('"');
        return sb;
    }

    private static void appendMap(StringBuilder sb, Map<String, Object> map, String indent) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append("{\n");
        var inner = indent + "  ";
        var i = 0;
        for (var e : map.entrySet()) {
            sb.append(inner).append('"').append(escape(e.getKey())).append("\": ");
            appendValue(sb, e.getValue(), inner);
            if (++i < map.size()) sb.append(',');
            sb.append('\n');
        }
        sb.append(indent).append('}');
    }

    private static void appendValue(StringBuilder sb, Object v, String indent) {
        if (v == null) {
            sb.append("null");
            return;
        }
        if (v instanceof Boolean || v instanceof Number) {
            sb.append(v);
            return;
        }
        if (v instanceof Collection<?> c) {
            if (c.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append('[');
            var i = 0;
            for (var item : c) {
                if (i > 0) sb.append(", ");
                appendValue(sb, item, indent);
                i++;
            }
            sb.append(']');
            return;
        }
        if (v instanceof Map) {
            @SuppressWarnings("unchecked")
            var m = (Map<String, Object>) v;
            appendMap(sb, m, indent);
            return;
        }
        sb.append('"').append(escape(v.toString())).append('"');
    }

    private static String escape(String s) {
        var sb = new StringBuilder(s.length() + 8);
        for (var i = 0; i < s.length(); i++) {
            var c = s.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    // finds "key": "value" at top level
    private static String extractString(String text, String key) {
        var needle = '"' + key + "\":";
        var idx = text.indexOf(needle);
        if (idx < 0) return null;
        var start = text.indexOf('"', idx + needle.length());
        if (start < 0) return null;
        var out = new StringBuilder();
        for (var i = start + 1; i < text.length(); i++) {
            var c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                var n = text.charAt(i + 1);
                switch (n) {
                    case '\\':
                        out.append('\\');
                        break;
                    case '"':
                        out.append('"');
                        break;
                    case 'n':
                        out.append('\n');
                        break;
                    case 'r':
                        out.append('\r');
                        break;
                    case 't':
                        out.append('\t');
                        break;
                    case 'u':
                        if (i + 5 < text.length()) {
                            out.append((char) Integer.parseInt(text.substring(i + 2, i + 6), 16));
                            i += 4; // plus the existing i++ below makes 5 total (for 'u' + 4 hex)
                        } else {
                            out.append('u');
                        }
                        break;
                    default:
                        out.append(n);
                }
                i++;
            } else if (c == '"') {
                return out.toString();
            } else {
                out.append(c);
            }
        }
        return null;
    }
}
