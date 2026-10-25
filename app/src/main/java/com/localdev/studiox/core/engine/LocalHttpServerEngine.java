package com.localdev.studiox.core.engine;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Genuine Local HTTP/1.1 Server Engine for LocalDev Studio X.
 * Supports configurable bind address (127.0.0.1 default), configurable port (8080 default),
 * configurable document root, static MIME serving, PHP CGI execution, path security validation,
 * port conflict detection, and real health check verification before reporting RUNNING state.
 */
public final class LocalHttpServerEngine {

    public static final String ENGINE_NAME = "LocalDev-HttpEngine (Native Java/Kotlin Socket Server)";
    public static final String ENGINE_VERSION = "1.4.0";

    private static final Set<String> SUPPORTED_METHODS = Set.of("GET", "POST", "HEAD", "OPTIONS");

    private volatile ServiceState state = ServiceState.STOPPED;
    private volatile String bindHost = "127.0.0.1";
    private volatile int port = 8080;
    private volatile File documentRoot;
    private volatile String lastErrorMessage = "";
    private volatile long startedAtEpochMs = 0L;

    private final PhpRuntimeEngine phpEngine;
    private final StructuredLogger logger;
    private final String appVerificationToken;
    private volatile String sqliteDatabasePath = "";

    private ServerSocket serverSocket;
    private ExecutorService workerPool;
    private Thread acceptThread;
    private final AtomicLong totalRequestsHandled = new AtomicLong(0);

    public LocalHttpServerEngine(File documentRoot, PhpRuntimeEngine phpEngine, StructuredLogger logger) {
        this.documentRoot = documentRoot;
        this.phpEngine = phpEngine;
        this.logger = logger;
        this.appVerificationToken = "LDX-VERIFY-" + UUID.randomUUID().toString();
    }

    public String getEngineName() { return ENGINE_NAME; }
    public String getEngineVersion() { return ENGINE_VERSION; }
    public ServiceState getState() { return state; }
    public String getBindHost() { return bindHost; }
    public int getPort() { return port; }
    public File getDocumentRoot() { return documentRoot; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public String getAppVerificationToken() { return appVerificationToken; }
    public long getTotalRequestsHandled() { return totalRequestsHandled.get(); }
    public long getUptimeSeconds() {
        if (state != ServiceState.RUNNING || startedAtEpochMs == 0L) return 0L;
        return Math.max(0L, (System.currentTimeMillis() - startedAtEpochMs) / 1000L);
    }

    public void setSqliteDatabasePath(String path) {
        this.sqliteDatabasePath = path != null ? path : "";
    }

    public synchronized void configure(String bindHost, int port, File documentRoot) {
        if (state == ServiceState.RUNNING || state == ServiceState.STARTING) {
            throw new IllegalStateException("Cannot mutate server configuration while server is " + state);
        }
        if (port < 1024 || port > 65535) {
            state = ServiceState.ERROR;
            lastErrorMessage = "Invalid port number: " + port + " (must be 1024-65535 for unprivileged Android app)";
            logger.error("HTTP", lastErrorMessage);
            throw new IllegalArgumentException(lastErrorMessage);
        }
        if (documentRoot == null || !documentRoot.exists() || !documentRoot.isDirectory()) {
            state = ServiceState.ERROR;
            lastErrorMessage = "Invalid document root directory: " + (documentRoot != null ? documentRoot.getAbsolutePath() : "null");
            logger.error("HTTP", lastErrorMessage);
            throw new IllegalArgumentException(lastErrorMessage);
        }
        this.bindHost = (bindHost == null || bindHost.isBlank()) ? "127.0.0.1" : bindHost.trim();
        this.port = port;
        this.documentRoot = documentRoot;
        this.lastErrorMessage = "";
    }

    public synchronized boolean start() {
        if (state == ServiceState.RUNNING) {
            logger.warn("HTTP", "Start requested while server is already RUNNING on port " + port);
            return true;
        }
        state = ServiceState.STARTING;
        lastErrorMessage = "";

        if (port < 1024 || port > 65535) {
            state = ServiceState.ERROR;
            lastErrorMessage = "Port out of unprivileged range (1024-65535): " + port;
            logger.error("HTTP", lastErrorMessage);
            return false;
        }
        if (documentRoot == null || !documentRoot.exists() || !documentRoot.isDirectory()) {
            state = ServiceState.ERROR;
            lastErrorMessage = "Document root does not exist: " + documentRoot;
            logger.error("HTTP", lastErrorMessage);
            return false;
        }

        try {
            InetAddress bindAddr = InetAddress.getByName(bindHost);
            // Probe if port is already actively listening before binding
            boolean portAlreadyOccupied = false;
            try (Socket probe = new Socket()) {
                probe.connect(new InetSocketAddress(bindAddr, port), 250);
                portAlreadyOccupied = true;
            } catch (IOException ignoredNotListening) {
                // Expected when port is free
            }
            if (portAlreadyOccupied) {
                throw new BindException("Address already in use (active listener detected on " + bindHost + ":" + port + ")");
            }
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(bindAddr, port), 50);

            workerPool = Executors.newFixedThreadPool(12);
            acceptThread = new Thread(this::acceptLoop, "LocalDev-HttpAccept-" + port);
            acceptThread.setDaemon(true);
            acceptThread.start();

            // Perform real socket health check before setting RUNNING
            if (!performSelfHealthCheck()) {
                stopInternal();
                state = ServiceState.ERROR;
                lastErrorMessage = "Server socket bound but failed internal health check.";
                logger.error("HTTP", lastErrorMessage);
                return false;
            }

            startedAtEpochMs = System.currentTimeMillis();
            state = ServiceState.RUNNING;
            logger.info("HTTP", String.format("Started %s v%s at http://%s:%d/ (docRoot=%s)",
                    ENGINE_NAME, ENGINE_VERSION, bindHost, port, documentRoot.getAbsolutePath()));
            return true;
        } catch (BindException be) {
            stopInternal();
            state = ServiceState.ERROR;
            lastErrorMessage = "Port conflict: Port " + port + " is already occupied (" + be.getMessage() + ")";
            logger.error("HTTP", lastErrorMessage);
            return false;
        } catch (IOException ioe) {
            stopInternal();
            state = ServiceState.ERROR;
            lastErrorMessage = "Failed to start HTTP server: " + ioe.getMessage();
            logger.error("HTTP", lastErrorMessage);
            return false;
        }
    }

    public synchronized boolean stop() {
        if (state == ServiceState.STOPPED) {
            return true;
        }
        state = ServiceState.STOPPING;
        logger.info("HTTP", "Stopping HTTP server on port " + port + "...");
        stopInternal();
        state = ServiceState.STOPPED;
        startedAtEpochMs = 0L;
        logger.info("HTTP", "HTTP server stopped cleanly.");
        return true;
    }

    public synchronized boolean restart() {
        logger.info("HTTP", "Restarting HTTP server...");
        stop();
        return start();
    }

    private void stopInternal() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}
        serverSocket = null;

        if (workerPool != null) {
            workerPool.shutdownNow();
            try {
                workerPool.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            workerPool = null;
        }
        if (acceptThread != null) {
            try {
                acceptThread.join(1000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            acceptThread = null;
        }
    }

    private boolean performSelfHealthCheck() {
        String checkHost = bindHost.equals("0.0.0.0") ? "127.0.0.1" : bindHost;
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(checkHost, port), 1500);
            s.setSoTimeout(1500);
            OutputStream out = s.getOutputStream();
            out.write(("OPTIONS / HTTP/1.1\r\nHost: " + checkHost + "\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = s.getInputStream();
            byte[] buf = new byte[128];
            int r = in.read(buf);
            if (r <= 0) return false;
            String resp = new String(buf, 0, r, StandardCharsets.US_ASCII);
            return resp.startsWith("HTTP/1.1 200") || resp.startsWith("HTTP/1.1 204");
        } catch (IOException e) {
            return false;
        }
    }

    private void acceptLoop() {
        while (serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                client.setSoTimeout(10000);
                if (workerPool != null && !workerPool.isShutdown()) {
                    workerPool.submit(() -> handleClient(client));
                } else {
                    client.close();
                }
            } catch (SocketException se) {
                break; // Socket closed during shutdown
            } catch (IOException e) {
                logger.error("HTTP", "Accept loop error: " + e.getMessage());
            }
        }
    }

    private void handleClient(Socket client) {
        totalRequestsHandled.incrementAndGet();
        String remoteIp = client.getInetAddress() != null ? client.getInetAddress().getHostAddress() : "127.0.0.1";
        try (Socket sock = client;
             InputStream rawIn = new BufferedInputStream(sock.getInputStream());
             OutputStream out = sock.getOutputStream()) {

            String requestLine = readAsciiLine(rawIn, 4096);
            if (requestLine == null || requestLine.isEmpty()) {
                sendTextResponse(out, 400, "Bad Request", "Empty HTTP request line", false);
                return;
            }

            String[] parts = requestLine.split("\\s+");
            if (parts.length < 2 || parts.length > 3) {
                logger.security("HTTP", "Malformed request line from " + remoteIp + ": " + requestLine);
                sendTextResponse(out, 400, "Bad Request", "Malformed HTTP request line", false);
                return;
            }

            String method = parts[0].toUpperCase(Locale.ROOT);
            String fullUri = parts[1];

            // Parse request headers
            Map<String, String> headers = new LinkedHashMap<>();
            int headerCount = 0;
            while (headerCount < 100) {
                String line = readAsciiLine(rawIn, 4096);
                if (line == null || line.isEmpty()) break;
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String hKey = line.substring(0, colon).trim();
                    String hVal = line.substring(colon + 1).trim();
                    headers.put(hKey, hVal);
                }
                headerCount++;
            }

            if ("OPTIONS".equals(method)) {
                sendOptionsResponse(out);
                return;
            }

            if (!SUPPORTED_METHODS.contains(method)) {
                logger.warn("HTTP", "405 Method Not Allowed: " + method + " " + fullUri);
                sendTextResponse(out, 405, "Method Not Allowed", "HTTP method " + method + " is not supported.", false);
                return;
            }

            boolean headOnly = "HEAD".equals(method);

            // Split path and query string
            String rawPath = fullUri;
            String queryString = "";
            int qIdx = fullUri.indexOf('?');
            if (qIdx >= 0) {
                rawPath = fullUri.substring(0, qIdx);
                queryString = fullUri.substring(qIdx + 1);
            }

            // Read request body if POST
            byte[] requestBody = new byte[0];
            String contentLengthVal = getHeaderIgnoreCase(headers, "Content-Length");
            if (contentLengthVal != null) {
                int len = Integer.parseInt(contentLengthVal.trim());
                if (len < 0 || len > 16 * 1024 * 1024) {
                    sendTextResponse(out, 413, "Payload Too Large", "Request body exceeds maximum permitted size", headOnly);
                    return;
                }
                requestBody = rawIn.readNBytes(len);
            }

            // Path Security Validation
            PathSecurityValidator.ValidationResult val = PathSecurityValidator.validateRequestPath(documentRoot, rawPath);
            if (!val.allowed) {
                if (val.httpStatus == 403) {
                    logger.security("HTTP", String.format("403 Forbidden [%s %s] from %s: %s", method, fullUri, remoteIp, val.reason));
                    sendTextResponse(out, 403, "Forbidden", "403 Forbidden: " + val.reason, headOnly);
                } else if (val.httpStatus == 404) {
                    logger.access("HTTP", String.format("404 Not Found [%s %s] from %s", method, fullUri, remoteIp));
                    sendTextResponse(out, 404, "Not Found", "404 Not Found: " + rawPath, headOnly);
                } else {
                    logger.warn("HTTP", String.format("%d Error [%s %s] from %s: %s", val.httpStatus, method, fullUri, remoteIp, val.reason));
                    sendTextResponse(out, val.httpStatus, "Bad Request", val.reason, headOnly);
                }
                return;
            }

            File targetFile = val.resolvedFile;
            String lowerName = targetFile.getName().toLowerCase(Locale.ROOT);

            // Route .php files exclusively through PhpRuntimeEngine — NEVER serve .php as static text!
            if (lowerName.endsWith(".php")) {
                if (phpEngine == null) {
                    logger.error("HTTP", "503 Service Unavailable: PHP Engine is not attached; refusing to disclose " + targetFile.getName());
                    sendTextResponse(out, 503, "Service Unavailable", "503 Service Unavailable: PHP Runtime unavailable", headOnly);
                    return;
                }
                PhpRuntimeEngine.PhpResponse phpResp = phpEngine.executeScript(
                        documentRoot,
                        targetFile,
                        method,
                        fullUri,
                        queryString,
                        headers,
                        requestBody,
                        remoteIp,
                        port,
                        appVerificationToken,
                        sqliteDatabasePath
                );
                logger.access("HTTP", String.format("%d [%s %s] (PHP %d bytes) from %s",
                        phpResp.httpStatus, method, fullUri, phpResp.body.length, remoteIp));
                sendRawResponse(out, phpResp.httpStatus, statusPhrase(phpResp.httpStatus),
                        phpResp.contentType, phpResp.headers, phpResp.body, headOnly);
                return;
            }

            // Serve static file
            String mimeType = resolveMimeType(lowerName);
            byte[] fileBytes;
            try (FileInputStream fis = new FileInputStream(targetFile)) {
                fileBytes = fis.readAllBytes();
            }
            logger.access("HTTP", String.format("200 [%s %s] (%s, %d bytes) from %s",
                    method, fullUri, mimeType, fileBytes.length, remoteIp));
            sendRawResponse(out, 200, "OK", mimeType, Collections.emptyMap(), fileBytes, headOnly);

        } catch (SocketTimeoutException ste) {
            logger.warn("HTTP", "Client socket timed out from " + remoteIp);
        } catch (Exception e) {
            logger.error("HTTP", "Error handling request from " + remoteIp + ": " + e.getMessage());
        }
    }

    private static String getHeaderIgnoreCase(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) return e.getValue();
        }
        return null;
    }

    private static String readAsciiLine(InputStream in, int maxLen) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(128);
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') {
                buf.write(b);
                if (buf.size() >= maxLen) {
                    throw new IOException("HTTP line exceeds maximum length");
                }
            }
        }
        if (b == -1 && buf.size() == 0) return null;
        return buf.toString(StandardCharsets.US_ASCII);
    }

    private static void sendOptionsResponse(OutputStream out) throws IOException {
        String hdr = "HTTP/1.1 204 No Content\r\n" +
                "Server: LocalDevStudioX/1.4.0\r\n" +
                "Allow: GET, POST, HEAD, OPTIONS\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n\r\n";
        out.write(hdr.getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private static void sendTextResponse(OutputStream out, int code, String phrase, String text, boolean headOnly) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        sendRawResponse(out, code, phrase, "text/plain; charset=utf-8", Collections.emptyMap(), body, headOnly);
    }

    private static void sendRawResponse(
            OutputStream out,
            int code,
            String phrase,
            String contentType,
            Map<String, List<String>> extraHeaders,
            byte[] body,
            boolean headOnly
    ) throws IOException {
        StringBuilder sb = new StringBuilder(256);
        sb.append("HTTP/1.1 ").append(code).append(' ').append(phrase).append("\r\n");
        sb.append("Server: LocalDevStudioX/1.4.0\r\n");
        sb.append("Content-Type: ").append(contentType != null ? contentType : "application/octet-stream").append("\r\n");
        sb.append("Content-Length: ").append(body != null ? body.length : 0).append("\r\n");
        sb.append("X-Content-Type-Options: nosniff\r\n");
        sb.append("X-Frame-Options: SAMEORIGIN\r\n");
        if (extraHeaders != null) {
            for (Map.Entry<String, List<String>> entry : extraHeaders.entrySet()) {
                for (String v : entry.getValue()) {
                    sb.append(entry.getKey()).append(": ").append(v).append("\r\n");
                }
            }
        }
        sb.append("Connection: close\r\n\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        if (!headOnly && body != null && body.length > 0) {
            out.write(body);
        }
        out.flush();
    }

    private static String statusPhrase(int code) {
        return switch (code) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 500 -> "Internal Server Error";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> "Response";
        };
    }

    public static String resolveMimeType(String lowerName) {
        if (lowerName.endsWith(".html") || lowerName.endsWith(".htm")) return "text/html; charset=utf-8";
        if (lowerName.endsWith(".css")) return "text/css; charset=utf-8";
        if (lowerName.endsWith(".js") || lowerName.endsWith(".mjs")) return "application/javascript; charset=utf-8";
        if (lowerName.endsWith(".json")) return "application/json; charset=utf-8";
        if (lowerName.endsWith(".svg")) return "image/svg+xml";
        if (lowerName.endsWith(".png")) return "image/png";
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) return "image/jpeg";
        if (lowerName.endsWith(".gif")) return "image/gif";
        if (lowerName.endsWith(".webp")) return "image/webp";
        if (lowerName.endsWith(".ico")) return "image/x-icon";
        if (lowerName.endsWith(".woff2")) return "font/woff2";
        if (lowerName.endsWith(".woff")) return "font/woff";
        if (lowerName.endsWith(".ttf")) return "font/ttf";
        if (lowerName.endsWith(".xml")) return "application/xml; charset=utf-8";
        if (lowerName.endsWith(".txt") || lowerName.endsWith(".md")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }
}
