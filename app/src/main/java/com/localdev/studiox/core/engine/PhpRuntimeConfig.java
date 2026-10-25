package com.localdev.studiox.core.engine;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration model for the PHP runtime (php.ini generator and runtime limits).
 */
public final class PhpRuntimeConfig {
    private String phpBinaryPath;
    private int maxExecutionTimeSeconds = 15;
    private String memoryLimit = "128M";
    private boolean displayErrors = true;
    private String postMaxSize = "16M";
    private String uploadMaxFilesize = "16M";
    private String defaultTimezone = "UTC";
    private boolean enablePhpInfoDiagnostic = false;
    private String sessionSavePath;

    public PhpRuntimeConfig(String phpBinaryPath, String sessionSavePath) {
        this.phpBinaryPath = phpBinaryPath;
        this.sessionSavePath = sessionSavePath;
    }

    public String getPhpBinaryPath() { return phpBinaryPath; }
    public void setPhpBinaryPath(String phpBinaryPath) { this.phpBinaryPath = phpBinaryPath; }

    public int getMaxExecutionTimeSeconds() { return maxExecutionTimeSeconds; }
    public void setMaxExecutionTimeSeconds(int maxExecutionTimeSeconds) {
        if (maxExecutionTimeSeconds < 1 || maxExecutionTimeSeconds > 300) {
            throw new IllegalArgumentException("max_execution_time must be between 1 and 300 seconds");
        }
        this.maxExecutionTimeSeconds = maxExecutionTimeSeconds;
    }

    public String getMemoryLimit() { return memoryLimit; }
    public void setMemoryLimit(String memoryLimit) { this.memoryLimit = memoryLimit; }

    public boolean isDisplayErrors() { return displayErrors; }
    public void setDisplayErrors(boolean displayErrors) { this.displayErrors = displayErrors; }

    public String getPostMaxSize() { return postMaxSize; }
    public void setPostMaxSize(String postMaxSize) { this.postMaxSize = postMaxSize; }

    public String getUploadMaxFilesize() { return uploadMaxFilesize; }
    public void setUploadMaxFilesize(String uploadMaxFilesize) { this.uploadMaxFilesize = uploadMaxFilesize; }

    public String getDefaultTimezone() { return defaultTimezone; }
    public void setDefaultTimezone(String defaultTimezone) { this.defaultTimezone = defaultTimezone; }

    public boolean isEnablePhpInfoDiagnostic() { return enablePhpInfoDiagnostic; }
    public void setEnablePhpInfoDiagnostic(boolean enablePhpInfoDiagnostic) {
        this.enablePhpInfoDiagnostic = enablePhpInfoDiagnostic;
    }

    public String getSessionSavePath() { return sessionSavePath; }

    public Map<String, String> toIniDirectives() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("engine", "On");
        map.put("max_execution_time", String.valueOf(maxExecutionTimeSeconds));
        map.put("memory_limit", memoryLimit);
        map.put("display_errors", displayErrors ? "On" : "Off");
        map.put("log_errors", "On");
        map.put("post_max_size", postMaxSize);
        map.put("upload_max_filesize", uploadMaxFilesize);
        map.put("date.timezone", defaultTimezone);
        map.put("expose_php", "Off");
        map.put("allow_url_include", "Off");
        map.put("cgi.force_redirect", "0");
        if (sessionSavePath != null && !sessionSavePath.isEmpty()) {
            map.put("session.save_path", "\"" + sessionSavePath + "\"");
        }
        return map;
    }

    public File writePhpIniFile(File configDir) throws IOException {
        if (!configDir.exists() && !configDir.mkdirs()) {
            throw new IOException("Unable to create PHP configuration directory: " + configDir);
        }
        File iniFile = new File(configDir, "php.ini");
        try (FileWriter fw = new FileWriter(iniFile, false)) {
            fw.write("; LocalDev Studio X — Generated php.ini (Do not place inside public web root)\n");
            for (Map.Entry<String, String> entry : toIniDirectives().entrySet()) {
                fw.write(entry.getKey() + " = " + entry.getValue() + "\n");
            }
        }
        return iniFile;
    }
}
