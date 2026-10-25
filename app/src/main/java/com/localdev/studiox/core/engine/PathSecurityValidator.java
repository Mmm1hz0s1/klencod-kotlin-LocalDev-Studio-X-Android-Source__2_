package com.localdev.studiox.core.engine;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Security validator enforcing document root confinement, directory traversal rejection,
 * hidden/sensitive file blocking, and source disclosure rules.
 */
public final class PathSecurityValidator {

    private static final Set<String> FORBIDDEN_EXTENSIONS = Set.of(
        ".env", ".ini", ".key", ".pem", ".sqlite", ".sqlite3", ".db", ".db-journal", ".db-wal", ".bak", ".conf", ".sh"
    );

    private static final Set<String> FORBIDDEN_FILENAMES = Set.of(
        "php.ini", "my.cnf", ".htaccess", ".htpasswd", "id_rsa", "credentials.json", "local.properties"
    );

    public static final class ValidationResult {
        public final boolean allowed;
        public final int httpStatus;
        public final String reason;
        public final File resolvedFile;

        private ValidationResult(boolean allowed, int httpStatus, String reason, File resolvedFile) {
            this.allowed = allowed;
            this.httpStatus = httpStatus;
            this.reason = reason;
            this.resolvedFile = resolvedFile;
        }

        public static ValidationResult ok(File resolved) {
            return new ValidationResult(true, 200, "OK", resolved);
        }

        public static ValidationResult deny(int status, String reason) {
            return new ValidationResult(false, status, reason, null);
        }
    }

    public static ValidationResult validateRequestPath(File docRoot, String rawUriPath) {
        if (docRoot == null || !docRoot.exists() || !docRoot.isDirectory()) {
            return ValidationResult.deny(500, "Document root is invalid or does not exist");
        }
        if (rawUriPath == null || rawUriPath.isEmpty()) {
            return ValidationResult.deny(400, "Empty URI path");
        }

        try {
            // Decode URL-encoded sequences twice to catch double-encoding traversal attacks (%252e%252e)
            String decodedOnce = URLDecoder.decode(rawUriPath, StandardCharsets.UTF_8);
            String decodedTwice = URLDecoder.decode(decodedOnce, StandardCharsets.UTF_8);

            if (rawUriPath.contains("..") || decodedOnce.contains("..") || decodedTwice.contains("..")
                    || decodedTwice.contains("\0") || decodedTwice.contains("\\")) {
                return ValidationResult.deny(403, "Directory traversal attempt blocked");
            }

            String cleanRel = decodedTwice.startsWith("/") ? decodedTwice.substring(1) : decodedTwice;
            if (cleanRel.isEmpty()) {
                cleanRel = "";
            }

            // Block dotfiles (.git, .env, etc.)
            String[] segments = cleanRel.split("/");
            for (String seg : segments) {
                if (seg.startsWith(".") && !seg.equals(".")) {
                    return ValidationResult.deny(403, "Access to hidden dotfile or directory is forbidden");
                }
            }

            File canonicalRoot = docRoot.getCanonicalFile();
            File target = cleanRel.isEmpty() ? canonicalRoot : new File(canonicalRoot, cleanRel).getCanonicalFile();

            String rootPrefix = canonicalRoot.getPath() + File.separator;
            if (!target.equals(canonicalRoot) && !target.getPath().startsWith(rootPrefix)) {
                return ValidationResult.deny(403, "Resolved path escapes document root boundary");
            }

            if (target.isDirectory()) {
                File indexPhp = new File(target, "index.php");
                File indexHtml = new File(target, "index.html");
                if (indexPhp.exists() && indexPhp.isFile()) {
                    target = indexPhp.getCanonicalFile();
                } else if (indexHtml.exists() && indexHtml.isFile()) {
                    target = indexHtml.getCanonicalFile();
                } else {
                    return ValidationResult.deny(403, "Directory listing is disabled by security policy");
                }
            }

            String lowerName = target.getName().toLowerCase(Locale.ROOT);
            if (FORBIDDEN_FILENAMES.contains(lowerName)) {
                return ValidationResult.deny(403, "Access to sensitive configuration file is forbidden");
            }
            for (String ext : FORBIDDEN_EXTENSIONS) {
                if (lowerName.endsWith(ext)) {
                    return ValidationResult.deny(403, "Access to protected file extension (" + ext + ") is forbidden");
                }
            }

            if (!target.exists() || !target.isFile()) {
                return ValidationResult.deny(404, "Requested resource not found");
            }

            return ValidationResult.ok(target);
        } catch (IllegalArgumentException | IOException e) {
            return ValidationResult.deny(400, "Malformed URI path: " + e.getMessage());
        }
    }
}
