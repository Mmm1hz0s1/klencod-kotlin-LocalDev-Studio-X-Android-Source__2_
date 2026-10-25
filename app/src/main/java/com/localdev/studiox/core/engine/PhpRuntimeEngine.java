package com.localdev.studiox.core.engine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Genuine PHP CGI/CLI Process Bridge Engine.
 * Executes .php files through a compiled PHP binary (such as libphp_cgi.so on Android or php-cgi on host),
 * never returning raw PHP source code on failure.
 */
public final class PhpRuntimeEngine {

    public static final class PhpResponse {
        public final int httpStatus;
        public final String contentType;
        public final Map<String, List<String>> headers;
        public final byte[] body;
        public final String stderrLog;
        public final boolean timedOut;

        public PhpResponse(int httpStatus, String contentType, Map<String, List<String>> headers,
                           byte[] body, String stderrLog, boolean timedOut) {
            this.httpStatus = httpStatus;
            this.contentType = contentType;
            this.headers = headers;
            this.body = body;
            this.stderrLog = stderrLog;
            this.timedOut = timedOut;
        }
    }

    private final PhpRuntimeConfig config;
    private final StructuredLogger logger;
    private final File runtimeConfigDir;
    private volatile ServiceState state = ServiceState.STOPPED;
    private volatile String detectedVersion = "Not Detected";
    private volatile List<String> loadedExtensions = Collections.emptyList();

    public PhpRuntimeEngine(PhpRuntimeConfig config, File runtimeConfigDir, StructuredLogger logger) {
        this.config = config;
        this.runtimeConfigDir = runtimeConfigDir;
        this.logger = logger;
    }

    public synchronized boolean initializeAndVerify() {
        state = ServiceState.STARTING;
        try {
            File sessionDir = new File(config.getSessionSavePath());
            if (!sessionDir.exists()) {
                sessionDir.mkdirs();
            }
            File iniFile = config.writePhpIniFile(runtimeConfigDir);
            String binPath = config.getPhpBinaryPath();
            if (binPath == null || binPath.isEmpty()) {
                state = ServiceState.ERROR;
                logger.error("PHP", "PHP binary path is not configured.");
                return false;
            }
            File binFile = new File(binPath);
            if (!binFile.exists() || !binFile.canExecute()) {
                state = ServiceState.ERROR;
                logger.error("PHP", "PHP binary missing or not executable at: " + binPath);
                return false;
            }

            // Query version (-v)
            ProcessBuilder pbVer = new ProcessBuilder(binPath, "-c", iniFile.getAbsolutePath(), "-v");
            Process procVer = pbVer.start();
            boolean finishedVer = procVer.waitFor(5, TimeUnit.SECONDS);
            if (!finishedVer) {
                procVer.destroyForcibly();
                state = ServiceState.ERROR;
                logger.error("PHP", "Timeout querying PHP binary version.");
                return false;
            }
            String verOutput = new String(procVer.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (procVer.exitValue() != 0 || verOutput.isEmpty()) {
                state = ServiceState.ERROR;
                logger.error("PHP", "PHP binary failed version check (exit " + procVer.exitValue() + ").");
                return false;
            }
            String firstLine = verOutput.split("\r?\n")[0].trim();
            this.detectedVersion = firstLine;

            // Query installed extensions (-m)
            ProcessBuilder pbMod = new ProcessBuilder(binPath, "-c", iniFile.getAbsolutePath(), "-m");
            Process procMod = pbVer.start();
            Process procModReal = pbMod.start();
            if (procModReal.waitFor(5, TimeUnit.SECONDS)) {
                String modOutput = new String(procModReal.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                List<String> mods = new ArrayList<>();
                for (String line : modOutput.split("\r?\n")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("[")) {
                        mods.add(trimmed);
                    }
                }
                this.loadedExtensions = Collections.unmodifiableList(mods);
            }

            state = ServiceState.RUNNING;
            logger.info("PHP", "PHP Runtime verified: " + detectedVersion + " | Extensions: " + loadedExtensions.size());
            return true;
        } catch (Exception e) {
            state = ServiceState.ERROR;
            logger.error("PHP", "Failed to initialize PHP runtime: " + e.getMessage());
            return false;
        }
    }

    public synchronized void stop() {
        state = ServiceState.STOPPED;
        logger.info("PHP", "PHP Runtime stopped.");
    }

    public ServiceState getState() { return state; }
    public String getDetectedVersion() { return detectedVersion; }
    public List<String> getLoadedExtensions() { return loadedExtensions; }
    public PhpRuntimeConfig getConfig() { return config; }

    public PhpResponse executeScript(
            File docRoot,
            File scriptFile,
            String requestMethod,
            String requestUri,
            String queryString,
            Map<String, String> requestHeaders,
            byte[] requestBody,
            String remoteAddr,
            int serverPort,
            String appVerificationToken,
            String sqliteDatabasePath
    ) {
        // Security Rule 13: Never disclose raw PHP source code even if PHP runtime is stopped or fails.
        if (state != ServiceState.RUNNING) {
            logger.error("PHP", "Rejected execution of " + scriptFile.getName() + " because PHP runtime state is " + state);
            byte[] errBytes = ("503 Service Unavailable: PHP Runtime is not running (State: " + state + "). Raw source disclosure prevented.")
                    .getBytes(StandardCharsets.UTF_8);
            return new PhpResponse(503, "text/plain; charset=utf-8", Collections.emptyMap(), errBytes, "PHP runtime not running", false);
        }

        try {
            File iniFile = new File(runtimeConfigDir, "php.ini");
            if (!iniFile.exists()) {
                iniFile = config.writePhpIniFile(runtimeConfigDir);
            }

            ProcessBuilder pb = new ProcessBuilder(
                    config.getPhpBinaryPath(),
                    "-c", iniFile.getAbsolutePath(),
                    "-f", scriptFile.getCanonicalPath()
            );
            pb.directory(docRoot.getCanonicalFile());

            Map<String, String> env = pb.environment();
            env.put("GATEWAY_INTERFACE", "CGI/1.1");
            env.put("SERVER_SOFTWARE", "LocalDevStudioX-HttpEngine/1.4.0");
            env.put("SERVER_PROTOCOL", "HTTP/1.1");
            env.put("SERVER_NAME", "127.0.0.1");
            env.put("SERVER_PORT", String.valueOf(serverPort));
            env.put("REQUEST_METHOD", requestMethod != null ? requestMethod : "GET");
            env.put("REQUEST_URI", requestUri != null ? requestUri : "/");
            env.put("QUERY_STRING", queryString != null ? queryString : "");
            env.put("SCRIPT_FILENAME", scriptFile.getCanonicalPath());
            env.put("SCRIPT_NAME", requestUri != null && requestUri.contains("?")
                    ? requestUri.substring(0, requestUri.indexOf('?'))
                    : (requestUri != null ? requestUri : "/" + scriptFile.getName()));
            env.put("DOCUMENT_ROOT", docRoot.getCanonicalPath());
            env.put("REDIRECT_STATUS", "200");
            env.put("REMOTE_ADDR", remoteAddr != null ? remoteAddr : "127.0.0.1");
            env.put("LOCALDEV_VERIFICATION_TOKEN", appVerificationToken != null ? appVerificationToken : "");
            env.put("LOCALDEV_SQLITE_PATH", sqliteDatabasePath != null ? sqliteDatabasePath : "");
            env.put("LOCALDEV_SESSION_DIR", config.getSessionSavePath());

            if (requestBody != null && requestBody.length > 0) {
                env.put("CONTENT_LENGTH", String.valueOf(requestBody.length));
            } else {
                env.put("CONTENT_LENGTH", "0");
            }

            if (requestHeaders != null) {
                for (Map.Entry<String, String> hdr : requestHeaders.entrySet()) {
                    String key = hdr.getKey().toUpperCase(Locale.ROOT).replace('-', '_');
                    if (key.equals("CONTENT_TYPE")) {
                        env.put("CONTENT_TYPE", hdr.getValue());
                    } else if (!key.equals("CONTENT_LENGTH")) {
                        env.put("HTTP_" + key, hdr.getValue());
                    }
                }
            }

            Process process = pb.start();
            if (requestBody != null && requestBody.length > 0) {
                try (OutputStream os = process.getOutputStream()) {
                    os.write(requestBody);
                    os.flush();
                }
            } else {
                process.getOutputStream().close();
            }

            ByteArrayOutputStream stdoutBuf = new ByteArrayOutputStream();
            ByteArrayOutputStream stderrBuf = new ByteArrayOutputStream();

            Thread outReader = new Thread(() -> copyStream(process.getInputStream(), stdoutBuf));
            Thread errReader = new Thread(() -> copyStream(process.getErrorStream(), stderrBuf));
            outReader.start();
            errReader.start();

            boolean finished = process.waitFor(config.getMaxExecutionTimeSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                outReader.join(500);
                errReader.join(500);
                String timeoutMsg = "PHP script execution exceeded timeout limit of " + config.getMaxExecutionTimeSeconds() + " seconds.";
                logger.error("PHP", timeoutMsg + " (" + scriptFile.getName() + ")");
                return new PhpResponse(504, "text/plain; charset=utf-8", Collections.emptyMap(),
                        timeoutMsg.getBytes(StandardCharsets.UTF_8), timeoutMsg, true);
            }

            outReader.join(1000);
            errReader.join(1000);

            byte[] rawOut = stdoutBuf.toByteArray();
            String stderrStr = stderrBuf.toString(StandardCharsets.UTF_8).trim();
            if (!stderrStr.isEmpty()) {
                logger.warn("PHP", "PHP stderr (" + scriptFile.getName() + "): " + stderrStr);
            }

            if (process.exitValue() != 0 && rawOut.length == 0) {
                String errBody = config.isDisplayErrors()
                        ? ("PHP Execution Error (Exit " + process.exitValue() + "):\n" + stderrStr)
                        : "500 Internal Server Error (PHP execution failed)";
                return new PhpResponse(500, "text/plain; charset=utf-8", Collections.emptyMap(),
                        errBody.getBytes(StandardCharsets.UTF_8), stderrStr, false);
            }

            return parseCgiOutput(rawOut, stderrStr);

        } catch (Exception e) {
            logger.error("PHP", "Exception executing PHP script " + scriptFile.getName() + ": " + e.getMessage());
            byte[] msg = ("500 Internal Server Error: Failed to execute PHP interpreter.").getBytes(StandardCharsets.UTF_8);
            return new PhpResponse(500, "text/plain; charset=utf-8", Collections.emptyMap(), msg, e.getMessage(), false);
        }
    }

    /**
     * Executes diagnostic phpinfo() only when explicitly triggered via local diagnostic action.
     */
    public PhpResponse executeLocalDiagnosticPhpInfo() {
        if (!config.isEnablePhpInfoDiagnostic()) {
            byte[] denied = "403 Forbidden: phpinfo() diagnostic is disabled in PhpRuntimeConfig.".getBytes(StandardCharsets.UTF_8);
            return new PhpResponse(403, "text/plain; charset=utf-8", Collections.emptyMap(), denied, "", false);
        }
        if (state != ServiceState.RUNNING) {
            byte[] err = "503 Service Unavailable: PHP runtime is not running.".getBytes(StandardCharsets.UTF_8);
            return new PhpResponse(503, "text/plain; charset=utf-8", Collections.emptyMap(), err, "", false);
        }
        try {
            File iniFile = new File(runtimeConfigDir, "php.ini");
            ProcessBuilder pb = new ProcessBuilder(
                    config.getPhpBinaryPath(),
                    "-c", iniFile.getAbsolutePath(),
                    "-r", "phpinfo();"
            );
            Process proc = pb.start();
            boolean ok = proc.waitFor(5, TimeUnit.SECONDS);
            if (!ok) {
                proc.destroyForcibly();
                return new PhpResponse(504, "text/plain; charset=utf-8", Collections.emptyMap(),
                        "Timeout running phpinfo()".getBytes(StandardCharsets.UTF_8), "", true);
            }
            byte[] out = proc.getInputStream().readAllBytes();
            logger.info("PHP", "Executed local-only phpinfo() diagnostic.");
            return new PhpResponse(200, "text/plain; charset=utf-8", Collections.emptyMap(), out, "", false);
        } catch (Exception e) {
            return new PhpResponse(500, "text/plain; charset=utf-8", Collections.emptyMap(),
                    e.getMessage().getBytes(StandardCharsets.UTF_8), e.getMessage(), false);
        }
    }

    private PhpResponse parseCgiOutput(byte[] rawOut, String stderrStr) {
        int headerEndIndex = findHeaderSeparator(rawOut);
        Map<String, List<String>> headers = new LinkedHashMap<>();
        int status = 200;
        String contentType = "text/html; charset=utf-8";
        byte[] body;

        if (headerEndIndex >= 0) {
            int sepLen = (rawOut[headerEndIndex] == '\r') ? 4 : 2;
            String headerBlock = new String(rawOut, 0, headerEndIndex, StandardCharsets.UTF_8);
            body = Arrays.copyOfRange(rawOut, headerEndIndex + sepLen, rawOut.length);

            for (String line : headerBlock.split("\r?\n")) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String name = line.substring(0, colon).trim();
                    String val = line.substring(colon + 1).trim();
                    if (name.equalsIgnoreCase("Status")) {
                        String[] parts = val.split("\\s+");
                        if (parts.length > 0) {
                            try { status = Integer.parseInt(parts[0]); } catch (NumberFormatException ignored) {}
                        }
                    } else if (name.equalsIgnoreCase("Content-Type")) {
                        contentType = val;
                    } else {
                        headers.computeIfAbsent(name, k -> new ArrayList<>()).add(val);
                    }
                }
            }
        } else {
            body = rawOut;
        }

        return new PhpResponse(status, contentType, headers, body, stderrStr, false);
    }

    private static int findHeaderSeparator(byte[] data) {
        String prefix = new String(data, 0, Math.min(data.length, 64), StandardCharsets.US_ASCII);
        if (!prefix.startsWith("Status:") && !prefix.startsWith("Content-Type:") && !prefix.startsWith("Content-type:")
                && !prefix.startsWith("X-") && !prefix.startsWith("Set-Cookie:") && !prefix.startsWith("Location:")
                && !prefix.startsWith("Cache-Control:")) {
            return -1;
        }
        for (int i = 0; i < data.length - 3; i++) {
            if (data[i] == '\r' && data[i + 1] == '\n' && data[i + 2] == '\r' && data[i + 3] == '\n') {
                return i;
            }
        }
        for (int i = 0; i < data.length - 1; i++) {
            if (data[i] == '\n' && data[i + 1] == '\n') {
                return i;
            }
        }
        return -1;
    }

    private static void copyStream(InputStream in, OutputStream out) {
        byte[] buf = new byte[4096];
        int r;
        try {
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
        } catch (IOException ignored) {}
    }
}
