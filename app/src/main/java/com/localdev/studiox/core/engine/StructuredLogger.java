package com.localdev.studiox.core.engine;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Bounded thread-safe structured logger for HTTP requests, PHP runtime, Database, and Security events.
 */
public final class StructuredLogger {

    public enum Level {
        INFO, WARN, ERROR, SECURITY, ACCESS
    }

    public static final class LogEntry {
        public final String timestamp;
        public final Level level;
        public final String component;
        public final String message;

        public LogEntry(String timestamp, Level level, String component, String message) {
            this.timestamp = timestamp;
            this.level = level;
            this.component = component;
            this.message = sanitizeSecrets(message);
        }

        private static String sanitizeSecrets(String raw) {
            if (raw == null) return "";
            return raw.replaceAll("(?i)(password|passwd|secret|token)\\s*[:=]\\s*[^\\s&,;]+", "$1=***REDACTED***");
        }

        @Override
        public String toString() {
            return String.format("[%s] [%s] [%s] %s", timestamp, level.name(), component, message);
        }
    }

    private final int maxEntries;
    private final CopyOnWriteArrayList<LogEntry> entries = new CopyOnWriteArrayList<>();
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

    public StructuredLogger(int maxEntries) {
        this.maxEntries = Math.max(50, maxEntries);
    }

    public void log(Level level, String component, String message) {
        LogEntry entry = new LogEntry(ISO_FMT.format(Instant.now()), level, component, message);
        entries.add(entry);
        while (entries.size() > maxEntries) {
            entries.remove(0);
        }
    }

    public void info(String component, String message) {
        log(Level.INFO, component, message);
    }

    public void warn(String component, String message) {
        log(Level.WARN, component, message);
    }

    public void error(String component, String message) {
        log(Level.ERROR, component, message);
    }

    public void security(String component, String message) {
        log(Level.SECURITY, component, message);
    }

    public void access(String component, String message) {
        log(Level.ACCESS, component, message);
    }

    public List<LogEntry> getEntries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public List<LogEntry> getEntriesByComponent(String component) {
        List<LogEntry> result = new ArrayList<>();
        for (LogEntry e : entries) {
            if (e.component.equalsIgnoreCase(component)) {
                result.add(e);
            }
        }
        return result;
    }

    public void clear() {
        entries.clear();
    }
}
