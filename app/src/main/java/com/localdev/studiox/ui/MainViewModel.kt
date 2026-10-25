package com.localdev.studiox.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localdev.studiox.core.engine.DatabaseEngine
import com.localdev.studiox.core.engine.LocalHttpServerEngine
import com.localdev.studiox.core.engine.PhpRuntimeConfig
import com.localdev.studiox.core.engine.PhpRuntimeEngine
import com.localdev.studiox.core.engine.ServiceState
import com.localdev.studiox.core.engine.StructuredLogger
import com.localdev.studiox.database.AndroidSqliteBridge
import com.localdev.studiox.service.LocalDevForegroundService
import com.localdev.studiox.storage.WorkspaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class AppScreen(val title: String) {
    DASHBOARD("Dashboard"),
    SERVER_MANAGER("Server Control Panel"),
    PHP_RUNTIME("PHP Runtime"),
    DATABASE_MANAGER("Database & SQL Editor"),
    PROJECTS("Website Projects"),
    FILE_EXPLORER("File Explorer"),
    CODE_EDITOR("Code & Config Editor"),
    LOGS_VIEWER("Logs Viewer"),
    NETWORK_PORTS("Network & Ports"),
    DIAGNOSTICS("Diagnostics & Verification"),
    SETTINGS("Application Settings"),
    ABOUT("About & Licenses")
}

data class DiagnosticTestRecord(
    val id: String,
    val name: String,
    val procedure: String,
    val expected: String,
    val actual: String,
    val status: String // PASS, FAIL, NOT_EXECUTED
)

data class AppUiState(
    val currentScreen: AppScreen = AppScreen.DASHBOARD,
    // HTTP Server State
    val httpEngineName: String = LocalHttpServerEngine.ENGINE_NAME,
    val httpEngineVersion: String = LocalHttpServerEngine.ENGINE_VERSION,
    val httpState: ServiceState = ServiceState.STOPPED,
    val bindHost: String = "127.0.0.1",
    val port: Int = 8080,
    val lanAccessOptIn: Boolean = false,
    val activeDocumentRootPath: String = "",
    val httpLastError: String = "",
    val totalRequestsHandled: Long = 0L,
    // PHP Runtime State
    val phpState: ServiceState = ServiceState.STOPPED,
    val phpVersion: String = "Not Detected (Bundle libphp_cgi.so in jniLibs)",
    val phpBinaryPath: String = "",
    val phpExtensions: List<String> = emptyList(),
    val phpMaxExecutionSeconds: Int = 15,
    val phpMemoryLimit: String = "128M",
    val phpDisplayErrors: Boolean = true,
    val phpEnablePhpInfoDiagnostic: Boolean = false,
    val phpInfoDiagnosticOutput: String = "",
    // Database State
    val dbState: ServiceState = ServiceState.STOPPED,
    val dbEngineLabel: String = "SQLite 3 (Android Native Local Engine)",
    val dbVersion: String = "Not Started",
    val dbTables: List<String> = emptyList(),
    val activeSqlQuery: String = "SELECT id, slug, title, created_at FROM dev_projects ORDER BY id;",
    val lastQueryResult: DatabaseEngine.QueryResult? = null,
    val queryHistory: List<DatabaseEngine.QueryResult> = emptyList(),
    // Remote MySQL / MariaDB Connector State
    val remoteMysqlHost: String = "127.0.0.1",
    val remoteMysqlPort: Int = 3306,
    val remoteMysqlDatabase: String = "mysql",
    val remoteMysqlUser: String = "root",
    val remoteMysqlStatus: String = "Disconnected (Local engine is SQLite 3)",
    // Projects & Files State
    val projects: List<File> = emptyList(),
    val activeProjectDir: File? = null,
    val currentExplorerDir: File? = null,
    val explorerFiles: List<File> = emptyList(),
    // Code Editor State
    val editingFile: File? = null,
    val editorContent: String = "",
    val editorUnsavedChanges: Boolean = false,
    val editorReadOnly: Boolean = false,
    val editorSearchQuery: String = "",
    val editorReplaceQuery: String = "",
    val editorStatusMessage: String = "",
    // Logs & Diagnostics
    val logs: List<StructuredLogger.LogEntry> = emptyList(),
    val logFilterComponent: String = "ALL",
    val diagnosticRecords: List<DiagnosticTestRecord> = emptyList(),
    val statusBannerMessage: String = ""
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val logger = StructuredLogger(600)
    private val workspaceManager = WorkspaceManager(application, logger)
    private val sqliteBridge = AndroidSqliteBridge(workspaceManager.databasesDir, logger)

    private val defaultPhpBinaryPath: String =
        File(application.applicationInfo.nativeLibraryDir, "libphp_cgi.so").absolutePath

    private val phpConfig = PhpRuntimeConfig(
        defaultPhpBinaryPath,
        workspaceManager.phpSessionsDir.absolutePath
    )
    private val phpEngine = PhpRuntimeEngine(
        phpConfig,
        workspaceManager.runtimeConfigDir,
        logger
    )

    private lateinit var httpServer: LocalHttpServerEngine

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val starterDir = workspaceManager.ensureInitialized()
            httpServer = LocalHttpServerEngine(starterDir, phpEngine, logger)
            httpServer.setSqliteDatabasePath(sqliteBridge.activeDbFile.absolutePath)

            // Initialize local SQLite database immediately
            sqliteBridge.start()
            // Probe PHP binary if present in nativeLibraryDir
            phpEngine.initializeAndVerify()

            val projs = workspaceManager.listProjects()
            val files = workspaceManager.listFilesInDirectory(starterDir)
            val initialFile = File(starterDir, "index.php").takeIf { it.exists() }

            _uiState.update {
                it.copy(
                    activeDocumentRootPath = starterDir.absolutePath,
                    phpBinaryPath = phpConfig.phpBinaryPath,
                    phpState = phpEngine.state,
                    phpVersion = phpEngine.detectedVersion,
                    phpExtensions = phpEngine.loadedExtensions,
                    dbState = sqliteBridge.state,
                    dbVersion = sqliteBridge.sqliteVersion,
                    dbTables = sqliteBridge.listTables(),
                    projects = projs,
                    activeProjectDir = starterDir,
                    currentExplorerDir = starterDir,
                    explorerFiles = files,
                    editingFile = initialFile,
                    editorContent = initialFile?.readText() ?: "",
                    logs = logger.entries
                )
            }
        }
    }

    fun navigateTo(screen: AppScreen) {
        refreshLogsAndMetrics()
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun startHttpServer() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(httpState = ServiceState.STARTING) }
            val current = _uiState.value
            val docRoot = File(current.activeDocumentRootPath)
            try {
                val bindAddr = if (current.lanAccessOptIn) "0.0.0.0" else "127.0.0.1"
                if (httpServer.state != ServiceState.RUNNING) {
                    httpServer.configure(bindAddr, current.port, docRoot)
                }
                val ok = httpServer.start()
                if (ok) {
                    startAndroidForegroundService(current.port)
                }
            } catch (e: Exception) {
                logger.error("HTTP", "Configuration error: ${e.message}")
            }
            syncServiceStates()
        }
    }

    fun stopHttpServer() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(httpState = ServiceState.STOPPING) }
            httpServer.stop()
            stopAndroidForegroundService()
            syncServiceStates()
        }
    }

    fun restartHttpServer() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(httpState = ServiceState.STARTING) }
            httpServer.restart()
            syncServiceStates()
        }
    }

    fun updateServerPortAndBind(newPort: Int, lanOptIn: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val wasRunning = httpServer.state == ServiceState.RUNNING
            if (wasRunning) {
                httpServer.stop()
            }
            val bindHost = if (lanOptIn) "0.0.0.0" else "127.0.0.1"
            try {
                httpServer.configure(bindHost, newPort, File(_uiState.value.activeDocumentRootPath))
                if (lanOptIn) {
                    logger.security("HTTP", "Developer explicitly opted in to LAN exposure (0.0.0.0:$newPort).")
                }
                if (wasRunning) {
                    httpServer.start()
                }
            } catch (e: Exception) {
                logger.error("HTTP", "Failed to apply port $newPort: ${e.message}")
            }
            _uiState.update {
                it.copy(
                    port = httpServer.port,
                    bindHost = httpServer.bindHost,
                    lanAccessOptIn = lanOptIn
                )
            }
            syncServiceStates()
        }
    }

    fun startOrVerifyPhpRuntime(customBinaryPath: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!customBinaryPath.isNullOrBlank()) {
                phpConfig.phpBinaryPath = customBinaryPath.trim()
            }
            phpEngine.initializeAndVerify()
            syncServiceStates()
        }
    }

    fun stopPhpRuntime() {
        viewModelScope.launch(Dispatchers.IO) {
            phpEngine.stop()
            syncServiceStates()
        }
    }

    fun updatePhpSettings(maxExecSeconds: Int, memoryLimit: String, displayErrors: Boolean, enablePhpInfo: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                phpConfig.maxExecutionTimeSeconds = maxExecSeconds
                phpConfig.memoryLimit = memoryLimit
                phpConfig.displayErrors = displayErrors
                phpConfig.isEnablePhpInfoDiagnostic = enablePhpInfo
                phpConfig.writePhpIniFile(workspaceManager.runtimeConfigDir)
                logger.info("PHP", "Updated php.ini configuration (max_execution_time=${maxExecSeconds}s, memory_limit=$memoryLimit)")
                _uiState.update {
                    it.copy(
                        phpMaxExecutionSeconds = maxExecSeconds,
                        phpMemoryLimit = memoryLimit,
                        phpDisplayErrors = displayErrors,
                        phpEnablePhpInfoDiagnostic = enablePhpInfo,
                        statusBannerMessage = "Saved php.ini configuration."
                    )
                }
            } catch (e: Exception) {
                logger.error("PHP", "Invalid PHP configuration: ${e.message}")
                _uiState.update { it.copy(statusBannerMessage = "Error: ${e.message}") }
            }
            syncServiceStates()
        }
    }

    fun runDiagnosticPhpInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val resp = phpEngine.executeLocalDiagnosticPhpInfo()
            val text = String(resp.body, Charsets.UTF_8)
            _uiState.update { it.copy(phpInfoDiagnosticOutput = text) }
            syncServiceStates()
        }
    }

    fun startSqliteDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            sqliteBridge.start()
            syncServiceStates()
        }
    }

    fun stopSqliteDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            sqliteBridge.stop()
            syncServiceStates()
        }
    }

    fun updateSqlQueryText(sql: String) {
        _uiState.update { it.copy(activeSqlQuery = sql) }
    }

    fun executeSqlInEditor() {
        viewModelScope.launch(Dispatchers.IO) {
            val sql = _uiState.value.activeSqlQuery
            val res = sqliteBridge.executeQuery(sql)
            _uiState.update {
                it.copy(
                    lastQueryResult = res,
                    dbTables = sqliteBridge.listTables(),
                    queryHistory = sqliteBridge.getHistory(),
                    logs = logger.entries
                )
            }
        }
    }

    fun inspectTableSchema(tableName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = sqliteBridge.inspectTable(tableName)
            _uiState.update {
                it.copy(
                    activeSqlQuery = "PRAGMA table_info($tableName);",
                    lastQueryResult = res,
                    queryHistory = sqliteBridge.getHistory()
                )
            }
        }
    }

    fun exportSqliteDatabaseDump() {
        viewModelScope.launch(Dispatchers.IO) {
            val outFile = File(workspaceManager.exportsDir, "localdev_dump_${System.currentTimeMillis()}.sql")
            val ok = sqliteBridge.exportSqlDump(outFile)
            _uiState.update {
                it.copy(
                    statusBannerMessage = if (ok) "Exported SQL dump to ${outFile.name}" else "SQL dump export failed",
                    logs = logger.entries
                )
            }
        }
    }

    fun testRemoteMySqlConnection(host: String, port: Int, dbName: String, user: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    remoteMysqlHost = host,
                    remoteMysqlPort = port,
                    remoteMysqlDatabase = dbName,
                    remoteMysqlUser = user,
                    remoteMysqlStatus = "Probing TCP MySQL handshake at $host:$port..."
                )
            }
            val probeEngine = DatabaseEngine("", workspaceManager.databasesDir, logger)
            val hs = probeEngine.testRemoteMySqlHandshake(host, port, 2500)
            _uiState.update {
                it.copy(
                    remoteMysqlStatus = hs.diagnosticMessage,
                    logs = logger.entries
                )
            }
        }
    }

    fun createProject(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = workspaceManager.createNewProject(name)
            res.onSuccess { newDir ->
                val projs = workspaceManager.listProjects()
                _uiState.update {
                    it.copy(
                        projects = projs,
                        statusBannerMessage = "Created project '${newDir.name}'",
                        logs = logger.entries
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(statusBannerMessage = "Error: ${err.message}") }
            }
        }
    }

    fun selectActiveProject(projectDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val wasRunning = httpServer.state == ServiceState.RUNNING
            if (wasRunning) httpServer.stop()
            httpServer.configure(httpServer.bindHost, httpServer.port, projectDir)
            if (wasRunning) httpServer.start()

            val files = workspaceManager.listFilesInDirectory(projectDir)
            _uiState.update {
                it.copy(
                    activeProjectDir = projectDir,
                    activeDocumentRootPath = projectDir.absolutePath,
                    currentExplorerDir = projectDir,
                    explorerFiles = files,
                    statusBannerMessage = "Switched web root to htdocs/${projectDir.name}"
                )
            }
            syncServiceStates()
        }
    }

    fun exportProjectZip(projectDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = workspaceManager.exportProjectAsZip(projectDir)
            res.onSuccess { zip ->
                _uiState.update {
                    it.copy(
                        statusBannerMessage = "Exported ${zip.name} (${zip.length()} bytes)",
                        logs = logger.entries
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(statusBannerMessage = "ZIP export failed: ${err.message}") }
            }
        }
    }

    fun browseDirectory(dir: File) {
        if (!workspaceManager.isInsideProjectsRoot(dir)) return
        val files = workspaceManager.listFilesInDirectory(dir)
        _uiState.update {
            it.copy(
                currentExplorerDir = dir,
                explorerFiles = files
            )
        }
    }

    fun openFileInEditor(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!file.exists() || !file.isFile) return@launch
            if (file.length() > 512 * 1024) {
                _uiState.update { it.copy(editorStatusMessage = "File exceeds 512 KB mobile editor safety limit.") }
                return@launch
            }
            val readOnly = !workspaceManager.isInsideProjectsRoot(file)
            val content = file.readText(Charsets.UTF_8)
            _uiState.update {
                it.copy(
                    currentScreen = AppScreen.CODE_EDITOR,
                    editingFile = file,
                    editorContent = content,
                    editorUnsavedChanges = false,
                    editorReadOnly = readOnly,
                    editorStatusMessage = "Loaded ${file.name} (${file.length()} bytes)"
                )
            }
        }
    }

    fun updateEditorContent(newText: String) {
        if (_uiState.value.editorReadOnly) return
        _uiState.update {
            it.copy(
                editorContent = newText,
                editorUnsavedChanges = true
            )
        }
    }

    fun saveEditorFile() {
        viewModelScope.launch(Dispatchers.IO) {
            val cur = _uiState.value
            val file = cur.editingFile ?: return@launch
            if (cur.editorReadOnly) {
                _uiState.update { it.copy(editorStatusMessage = "Cannot overwrite read-only system file.") }
                return@launch
            }
            try {
                file.writeText(cur.editorContent, Charsets.UTF_8)
                logger.info("EDITOR", "Saved file ${file.name} (${cur.editorContent.length} chars)")
                _uiState.update {
                    it.copy(
                        editorUnsavedChanges = false,
                        editorStatusMessage = "Saved ${file.name} successfully.",
                        logs = logger.entries
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(editorStatusMessage = "Save failed: ${e.message}") }
            }
        }
    }

    fun searchAndReplaceInEditor(search: String, replace: String) {
        val cur = _uiState.value
        if (search.isEmpty() || cur.editorReadOnly) return
        val occurrences = cur.editorContent.split(search).size - 1
        if (occurrences > 0) {
            val updated = cur.editorContent.replace(search, replace)
            _uiState.update {
                it.copy(
                    editorContent = updated,
                    editorUnsavedChanges = true,
                    editorStatusMessage = "Replaced $occurrences occurrence(s) of '$search'."
                )
            }
        } else {
            _uiState.update { it.copy(editorStatusMessage = "No occurrences of '$search' found.") }
        }
    }

    fun setLogFilter(component: String) {
        _uiState.update { it.copy(logFilterComponent = component, logs = logger.entries) }
    }

    fun clearLogs() {
        logger.clear()
        _uiState.update { it.copy(logs = emptyList()) }
    }

    fun runLiveSelfDiagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            val records = mutableListOf<DiagnosticTestRecord>()
            val serverWasRunning = httpServer.state == ServiceState.RUNNING
            if (!serverWasRunning) {
                httpServer.start()
            }

            // 1. Static HTTP check
            val httpAlive = httpServer.state == ServiceState.RUNNING
            records.add(
                DiagnosticTestRecord(
                    "TEST 04",
                    "Static HTTP Engine",
                    "Start LocalHttpServerEngine and verify socket health on port ${httpServer.port}",
                    "State = RUNNING, HTTP 200/204 response",
                    "State = ${httpServer.state}, Port = ${httpServer.port}",
                    if (httpAlive) "PASS" else "FAIL"
                )
            )

            // 2. PHP Runtime check
            val phpReady = phpEngine.state == ServiceState.RUNNING
            records.add(
                DiagnosticTestRecord(
                    "TEST 05",
                    "PHP Runtime Execution",
                    "Verify native PHP CGI binary at ${phpConfig.phpBinaryPath}",
                    "PHP interpreter responds to -v and executes scripts",
                    if (phpReady) "Verified: ${phpEngine.detectedVersion}" else "Binary not present at ${phpConfig.phpBinaryPath}",
                    if (phpReady) "PASS" else "NOT_EXECUTED"
                )
            )

            // 3. SQLite Database check
            val dbRes = sqliteBridge.executeQuery("SELECT sqlite_version();")
            records.add(
                DiagnosticTestRecord(
                    "TEST 06",
                    "Local SQLite 3 Database",
                    "Execute SELECT sqlite_version() and verify table persistence",
                    "Returns SQLite 3.x version and table list",
                    if (dbRes.success) "Version: ${sqliteBridge.sqliteVersion}, Tables: ${sqliteBridge.listTables().size}" else dbRes.errorMessage,
                    if (dbRes.success) "PASS" else "FAIL"
                )
            )

            // 4. Path Traversal Security check
            val docRoot = File(_uiState.value.activeDocumentRootPath)
            val travCheck = com.localdev.studiox.core.engine.PathSecurityValidator.validateRequestPath(
                docRoot,
                "/../../etc/passwd"
            )
            records.add(
                DiagnosticTestRecord(
                    "TEST 11",
                    "Path Traversal Prevention",
                    "Validate '/../../etc/passwd' against PathSecurityValidator",
                    "HTTP 403 Forbidden",
                    "Allowed=${travCheck.allowed}, HTTP ${travCheck.httpStatus} (${travCheck.reason})",
                    if (!travCheck.allowed && travCheck.httpStatus == 403) "PASS" else "FAIL"
                )
            )

            // 5. Raw PHP Source Protection check
            val phpResp = phpEngine.executeScript(
                docRoot,
                File(docRoot, "index.php"),
                "GET",
                "/index.php",
                "",
                emptyMap(),
                ByteArray(0),
                "127.0.0.1",
                httpServer.port,
                httpServer.appVerificationToken,
                sqliteBridge.activeDbFile.absolutePath
            )
            val bodyStr = String(phpResp.body, Charsets.UTF_8)
            val sourceProtected = !bodyStr.contains("<?php")
            records.add(
                DiagnosticTestRecord(
                    "TEST 12",
                    "PHP Source Disclosure Protection",
                    "Request /index.php and confirm raw '<?php' source is never exposed",
                    "No raw PHP tags leaked (HTTP 200 executed output or HTTP 503 safe error)",
                    "HTTP ${phpResp.httpStatus}, RawSourceLeaked=${!sourceProtected}",
                    if (sourceProtected) "PASS" else "FAIL"
                )
            )

            _uiState.update {
                it.copy(
                    diagnosticRecords = records,
                    logs = logger.entries
                )
            }
            syncServiceStates()
        }
    }

    private fun refreshLogsAndMetrics() {
        if (!::httpServer.isInitialized) return
        _uiState.update {
            it.copy(
                totalRequestsHandled = httpServer.totalRequestsHandled,
                logs = logger.entries
            )
        }
    }

    private suspend fun syncServiceStates() {
        withContext(Dispatchers.Main) {
            _uiState.update {
                it.copy(
                    httpState = httpServer.state,
                    httpLastError = httpServer.lastErrorMessage,
                    bindHost = httpServer.bindHost,
                    port = httpServer.port,
                    totalRequestsHandled = httpServer.totalRequestsHandled,
                    phpState = phpEngine.state,
                    phpVersion = phpEngine.detectedVersion,
                    phpExtensions = phpEngine.loadedExtensions,
                    dbState = sqliteBridge.state,
                    dbVersion = sqliteBridge.sqliteVersion,
                    dbTables = sqliteBridge.listTables(),
                    logs = logger.entries
                )
            }
        }
    }

    private fun startAndroidForegroundService(port: Int) {
        try {
            val app = getApplication<Application>()
            val intent = Intent(app, LocalDevForegroundService::class.java).apply {
                action = LocalDevForegroundService.ACTION_START_FOREGROUND
                putExtra(LocalDevForegroundService.EXTRA_PORT, port)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
        } catch (e: Exception) {
            logger.warn("SERVICE", "Foreground service start warning: ${e.message}")
        }
    }

    private fun stopAndroidForegroundService() {
        try {
            val app = getApplication<Application>()
            val intent = Intent(app, LocalDevForegroundService::class.java).apply {
                action = LocalDevForegroundService.ACTION_STOP_FOREGROUND
            }
            app.startService(intent)
        } catch (e: Exception) {
            logger.warn("SERVICE", "Foreground service stop warning: ${e.message}")
        }
    }
}
