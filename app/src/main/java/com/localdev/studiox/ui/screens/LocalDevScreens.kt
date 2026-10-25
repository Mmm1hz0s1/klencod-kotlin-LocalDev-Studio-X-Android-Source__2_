package com.localdev.studiox.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localdev.studiox.core.engine.ServiceState
import com.localdev.studiox.core.engine.SqlSyntaxHighlighter
import com.localdev.studiox.ui.AppScreen
import com.localdev.studiox.ui.AppUiState
import com.localdev.studiox.ui.MainViewModel
import com.localdev.studiox.ui.components.DevPanelCard
import com.localdev.studiox.ui.components.KeyValueRow
import com.localdev.studiox.ui.components.ServiceStatusBadge
import com.localdev.studiox.ui.theme.DevAccentBlue
import com.localdev.studiox.ui.theme.DevAccentCyan
import com.localdev.studiox.ui.theme.DevBackground
import com.localdev.studiox.ui.theme.DevError
import com.localdev.studiox.ui.theme.DevSuccess
import com.localdev.studiox.ui.theme.DevSurfacePrimary
import com.localdev.studiox.ui.theme.DevSurfaceSecondary
import com.localdev.studiox.ui.theme.DevTextPrimary
import com.localdev.studiox.ui.theme.DevTextSecondary
import com.localdev.studiox.ui.theme.DevWarning

// =========================================================================
// A. DASHBOARD SCREEN
// =========================================================================
@Composable
fun DashboardScreen(state: AppUiState, viewModel: MainViewModel) {
    val context = LocalContext.current
    val localUrl = "http://127.0.0.1:${state.port}/"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        DevPanelCard(
            title = "LocalDev Studio X — Stack Overview",
            subtitle = "Real-time measured service status (Never simulated)"
        ) {
            KeyValueRow("Local Endpoint URL", localUrl)
            KeyValueRow("ActiveBind Address", "${state.bindHost}:${state.port}")
            KeyValueRow("Active Web Root", state.activeDocumentRootPath.substringAfterLast("/projects/"))
            KeyValueRow("Hosted Projects Count", state.projects.size.toString())
            KeyValueRow("Total HTTP Requests Served", state.totalRequestsHandled.toString())

            if (state.httpLastError.isNotBlank()) {
                Text(
                    text = "Last Server Error: ${state.httpLastError}",
                    color = DevError,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.startHttpServer() },
                    enabled = state.httpState != ServiceState.RUNNING,
                    colors = ButtonDefaults.buttonColors(containerColor = DevAccentBlue)
                ) {
                    Text("Start HTTP")
                }
                OutlinedButton(
                    onClick = { viewModel.stopHttpServer() },
                    enabled = state.httpState == ServiceState.RUNNING
                ) {
                    Text("Stop HTTP")
                }
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(localUrl))
                        context.startActivity(intent)
                    },
                    enabled = state.httpState == ServiceState.RUNNING
                ) {
                    Text("Open Browser")
                }
            }
        }

        // Service summary cards
        DevPanelCard(
            title = "HTTP Server Engine",
            subtitle = "${state.httpEngineName} v${state.httpEngineVersion}",
            trailing = { ServiceStatusBadge(state.httpState) }
        ) {
            KeyValueRow("Port", state.port.toString())
            KeyValueRow("Security Confinement", "Document Root Enforced")
        }

        DevPanelCard(
            title = "PHP Runtime Engine",
            subtitle = state.phpVersion,
            trailing = { ServiceStatusBadge(state.phpState) }
        ) {
            KeyValueRow("Loaded Extensions", state.phpExtensions.size.toString())
            KeyValueRow("Max Execution Time", "${state.phpMaxExecutionSeconds}s")
        }

        DevPanelCard(
            title = "Database Engine (Local SQLite 3)",
            subtitle = state.dbEngineLabel,
            trailing = { ServiceStatusBadge(state.dbState) }
        ) {
            KeyValueRow("Engine Version", state.dbVersion)
            KeyValueRow("User Tables Count", state.dbTables.size.toString())
        }

        DevPanelCard(
            title = "Recent Structured Log Entries",
            subtitle = "Showing latest ${state.logs.takeLast(5).size} of ${state.logs.size} bounded events"
        ) {
            val recent = state.logs.takeLast(5).reversed()
            if (recent.isEmpty()) {
                Text("No log events recorded yet.", color = DevTextSecondary, fontSize = 12.sp)
            } else {
                for (entry in recent) {
                    Text(
                        text = entry.toString(),
                        color = DevTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

// =========================================================================
// B. SERVER CONTROL PANEL (XAMPP-INSPIRED SERVICE MANAGER)
// =========================================================================
@Composable
fun ServerControlPanelScreen(state: AppUiState, viewModel: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. HTTP Server Card
        DevPanelCard(
            title = "1. Local HTTP Web Server",
            subtitle = "Engine: ${state.httpEngineName}",
            trailing = { ServiceStatusBadge(state.httpState) }
        ) {
            KeyValueRow("Runtime Version", state.httpEngineVersion)
            KeyValueRow("Process State", state.httpState.name)
            KeyValueRow("Configured Port", "${state.bindHost}:${state.port}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.startHttpServer() }) { Text("Start") }
                OutlinedButton(onClick = { viewModel.stopHttpServer() }) { Text("Stop") }
                OutlinedButton(onClick = { viewModel.restartHttpServer() }) { Text("Restart") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.navigateTo(AppScreen.LOGS_VIEWER) }) { Text("Logs") }
                OutlinedButton(onClick = { viewModel.navigateTo(AppScreen.NETWORK_PORTS) }) { Text("Config") }
            }
        }

        // 2. PHP Runtime Card
        DevPanelCard(
            title = "2. PHP CGI / CLI Runtime",
            subtitle = "Engine: Native Android PHP CGI Bridge",
            trailing = { ServiceStatusBadge(state.phpState) }
        ) {
            KeyValueRow("Runtime Version", state.phpVersion)
            KeyValueRow("Process State", state.phpState.name)
            KeyValueRow("Binary Path", state.phpBinaryPath.substringAfterLast("/"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.startOrVerifyPhpRuntime() }) { Text("Start / Verify") }
                OutlinedButton(onClick = { viewModel.stopPhpRuntime() }) { Text("Stop") }
                OutlinedButton(onClick = { viewModel.navigateTo(AppScreen.PHP_RUNTIME) }) { Text("Config") }
            }
        }

        // 3. SQLite Local Database Card
        DevPanelCard(
            title = "3. SQLite 3 Local Database",
            subtitle = "Engine: ${state.dbEngineLabel}",
            trailing = { ServiceStatusBadge(state.dbState) }
        ) {
            KeyValueRow("Runtime Version", state.dbVersion)
            KeyValueRow("Process State", state.dbState.name)
            KeyValueRow("Storage Mode", "Embedded Persistent File (No TCP Port)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.startSqliteDatabase() }) { Text("Start") }
                OutlinedButton(onClick = { viewModel.stopSqliteDatabase() }) { Text("Stop") }
                OutlinedButton(onClick = { viewModel.navigateTo(AppScreen.DATABASE_MANAGER) }) { Text("SQL Admin") }
            }
        }
    }
}

// =========================================================================
// C. PHP RUNTIME SCREEN
// =========================================================================
@Composable
fun PhpRuntimeScreen(state: AppUiState, viewModel: MainViewModel) {
    var binPathInput by remember(state.phpBinaryPath) { mutableStateOf(state.phpBinaryPath) }
    var timeoutInput by remember(state.phpMaxExecutionSeconds) { mutableStateOf(state.phpMaxExecutionSeconds.toString()) }
    var memInput by remember(state.phpMemoryLimit) { mutableStateOf(state.phpMemoryLimit) }
    var displayErr by remember(state.phpDisplayErrors) { mutableStateOf(state.phpDisplayErrors) }
    var enablePhpInfo by remember(state.phpEnablePhpInfoDiagnostic) { mutableStateOf(state.phpEnablePhpInfoDiagnostic) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        DevPanelCard(
            title = "PHP Interpreter Status & ABI Integration",
            subtitle = "Executes .php requests via CGI/1.1 process isolation",
            trailing = { ServiceStatusBadge(state.phpState) }
        ) {
            KeyValueRow("Detected Version", state.phpVersion)
            KeyValueRow("Loaded Extensions", state.phpExtensions.joinToString(", ").ifEmpty { "None loaded" })
            OutlinedTextField(
                value = binPathInput,
                onValueChange = { binPathInput = it },
                label = { Text("PHP CGI Binary Path (libphp_cgi.so)") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { viewModel.startOrVerifyPhpRuntime(binPathInput) }) {
                Text("Verify & Initialize PHP Binary")
            }
        }

        DevPanelCard(
            title = "php.ini Directives Configuration",
            subtitle = "Stored outside public web root in runtime_config/php.ini"
        ) {
            OutlinedTextField(
                value = timeoutInput,
                onValueChange = { timeoutInput = it },
                label = { Text("max_execution_time (seconds, 1-300)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = memInput,
                onValueChange = { memInput = it },
                label = { Text("memory_limit (e.g., 128M)") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = displayErr, onCheckedChange = { displayErr = it })
                Text("display_errors = On (Development Mode)", color = DevTextPrimary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = enablePhpInfo, onCheckedChange = { enablePhpInfo = it })
                Text("Enable local-only phpinfo() diagnostic action", color = DevTextPrimary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val sec = timeoutInput.toIntOrNull() ?: 15
                    viewModel.updatePhpSettings(sec, memInput, displayErr, enablePhpInfo)
                }) {
                    Text("Save php.ini")
                }
                OutlinedButton(
                    onClick = { viewModel.runDiagnosticPhpInfo() },
                    enabled = enablePhpInfo
                ) {
                    Text("Run Local phpinfo()")
                }
            }
            if (state.phpInfoDiagnosticOutput.isNotBlank()) {
                Text(
                    text = state.phpInfoDiagnosticOutput.take(1500),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = DevTextSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DevBackground, RoundedCornerShape(6.dp))
                        .padding(8.dp)
                )
            }
        }
    }
}

// =========================================================================
// D. DATABASE MANAGER & SQL EDITOR SCREEN
// =========================================================================
@Composable
fun DatabaseManagerScreen(state: AppUiState, viewModel: MainViewModel) {
    var remoteHost by remember(state.remoteMysqlHost) { mutableStateOf(state.remoteMysqlHost) }
    var remotePort by remember(state.remoteMysqlPort) { mutableStateOf(state.remoteMysqlPort.toString()) }
    var remoteDb by remember(state.remoteMysqlDatabase) { mutableStateOf(state.remoteMysqlDatabase) }
    var remoteUser by remember(state.remoteMysqlUser) { mutableStateOf(state.remoteMysqlUser) }

    val highlightedSql = remember(state.activeSqlQuery) {
        val tokens = SqlSyntaxHighlighter.tokenize(state.activeSqlQuery)
        buildAnnotatedString {
            for (tok in tokens) {
                val color = when (tok.type) {
                    SqlSyntaxHighlighter.TokenType.KEYWORD -> DevAccentCyan
                    SqlSyntaxHighlighter.TokenType.FUNCTION -> DevAccentBlue
                    SqlSyntaxHighlighter.TokenType.STRING -> DevSuccess
                    SqlSyntaxHighlighter.TokenType.NUMBER -> DevWarning
                    SqlSyntaxHighlighter.TokenType.COMMENT -> DevTextSecondary
                    else -> DevTextPrimary
                }
                withStyle(SpanStyle(color = color, fontFamily = FontFamily.Monospace)) {
                    append(tok.text)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        DevPanelCard(
            title = "Local SQLite 3 Database Engine",
            subtitle = "Clearly labeled local embedded SQLite engine (Never misrepresented as MySQL)",
            trailing = { ServiceStatusBadge(state.dbState) }
        ) {
            KeyValueRow("Active Engine", state.dbVersion)
            KeyValueRow("Tables (${state.dbTables.size})", state.dbTables.joinToString(", ").ifEmpty { "No tables" })

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (tbl in state.dbTables) {
                    OutlinedButton(onClick = { viewModel.inspectTableSchema(tbl) }) {
                        Text("Inspect $tbl")
                    }
                }
                OutlinedButton(onClick = { viewModel.exportSqliteDatabaseDump() }) {
                    Text("Export .sql Dump")
                }
            }
        }

        DevPanelCard(
            title = "Interactive SQL Editor (CREATE, SELECT, INSERT, UPDATE, DELETE, ALTER, DROP)",
            subtitle = "Syntax-highlighted preview & tabular result inspector"
        ) {
            Text(
                text = highlightedSql,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DevBackground, RoundedCornerShape(6.dp))
                    .border(1.dp, DevSurfaceSecondary, RoundedCornerShape(6.dp))
                    .padding(10.dp)
            )
            OutlinedTextField(
                value = state.activeSqlQuery,
                onValueChange = { viewModel.updateSqlQueryText(it) },
                label = { Text("SQL Statement / Script") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.executeSqlInEditor() }) {
                    Text("Execute SQL")
                }
                OutlinedButton(onClick = {
                    viewModel.updateSqlQueryText("SELECT name, sql FROM sqlite_master WHERE type='table';")
                }) {
                    Text("Show Schema DDL")
                }
            }

            state.lastQueryResult?.let { res ->
                HorizontalDivider(color = DevSurfaceSecondary)
                if (!res.success) {
                    Text("SQL Error: ${res.errorMessage}", color = DevError, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                } else {
                    Text(
                        "Success (${res.executionTimeMs} ms) — Rows/Affected: ${res.affectedRows}",
                        color = DevSuccess,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (res.columns.isNotEmpty()) {
                        Text(
                            text = res.columns.joinToString(" | "),
                            color = DevAccentCyan,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        for (row in res.rows.take(25)) {
                            Text(
                                text = row.joinToString(" | "),
                                color = DevTextPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        DevPanelCard(
            title = "Remote MySQL / MariaDB Connection Inspector",
            subtitle = "Connects to external MySQL/MariaDB over TCP and verifies V10 wire greeting"
        ) {
            OutlinedTextField(
                value = remoteHost,
                onValueChange = { remoteHost = it },
                label = { Text("Remote MySQL/MariaDB Host") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = remotePort,
                onValueChange = { remotePort = it },
                label = { Text("Remote Port (default 3306)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = remoteDb,
                onValueChange = { remoteDb = it },
                label = { Text("Database Name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = remoteUser,
                onValueChange = { remoteUser = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = {
                viewModel.testRemoteMySqlConnection(remoteHost, remotePort.toIntOrNull() ?: 3306, remoteDb, remoteUser)
            }) {
                Text("Test Remote MySQL/MariaDB Handshake")
            }
            Text(
                text = state.remoteMysqlStatus,
                color = DevTextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }
    }
}

// =========================================================================
// E. WEBSITE PROJECTS SCREEN
// =========================================================================
@Composable
fun ProjectsScreen(state: AppUiState, viewModel: MainViewModel) {
    var newProjName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        DevPanelCard(
            title = "Create New Website Project",
            subtitle = "Deployed inside private htdocs/ workspace"
        ) {
            OutlinedTextField(
                value = newProjName,
                onValueChange = { newProjName = it },
                label = { Text("Project Folder Name (e.g., my_blog)") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = {
                viewModel.createProject(newProjName)
                newProjName = ""
            }) {
                Text("Create Project")
            }
        }

        DevPanelCard(
            title = "Available Website Projects (${state.projects.size})",
            subtitle = "Tap Activate to switch HTTP Document Root"
        ) {
            for (proj in state.projects) {
                val isActive = state.activeProjectDir?.absolutePath == proj.absolutePath
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isActive) DevSurfaceSecondary else DevBackground, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = proj.name + if (isActive) " [ACTIVE DOCROOT]" else "",
                            color = if (isActive) DevAccentCyan else DevTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "htdocs/${proj.name}",
                            color = DevTextSecondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { viewModel.selectActiveProject(proj) }) {
                            Text("Activate")
                        }
                        OutlinedButton(onClick = { viewModel.exportProjectZip(proj) }) {
                            Text("ZIP")
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// F. FILE EXPLORER SCREEN
// =========================================================================
@Composable
fun FileExplorerScreen(state: AppUiState, viewModel: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DevPanelCard(
            title = "Workspace File Explorer",
            subtitle = state.currentExplorerDir?.absolutePath?.substringAfterLast("/projects/") ?: "htdocs"
        ) {
            val parentDir = state.currentExplorerDir?.parentFile
            if (parentDir != null) {
                OutlinedButton(onClick = { viewModel.browseDirectory(parentDir) }) {
                    Text(".. (Up to Parent Directory)")
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.explorerFiles) { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DevBackground, RoundedCornerShape(6.dp))
                            .clickable {
                                if (file.isDirectory) {
                                    viewModel.browseDirectory(file)
                                } else {
                                    viewModel.openFileInEditor(file)
                                }
                            }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = (if (file.isDirectory) "[DIR] " else "[FILE] ") + file.name,
                            color = if (file.isDirectory) DevAccentCyan else DevTextPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                        if (file.isFile) {
                            Text(
                                text = "${file.length()} B",
                                color = DevTextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// G. CODE & CONFIGURATION EDITOR SCREEN
// =========================================================================
@Composable
fun CodeEditorScreen(state: AppUiState, viewModel: MainViewModel) {
    var searchInput by remember { mutableStateOf("") }
    var replaceInput by remember { mutableStateOf("") }

    val lineCount = remember(state.editorContent) {
        state.editorContent.lines().size.coerceAtLeast(1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DevPanelCard(
            title = "Code & Configuration Editor",
            subtitle = (state.editingFile?.name ?: "No file selected") +
                (if (state.editorUnsavedChanges) " *UNSAVED*" else "") +
                (if (state.editorReadOnly) " [READ-ONLY]" else "")
        ) {
            if (state.editorStatusMessage.isNotBlank()) {
                Text(state.editorStatusMessage, color = DevAccentCyan, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchInput,
                    onValueChange = { searchInput = it },
                    label = { Text("Find") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = replaceInput,
                    onValueChange = { replaceInput = it },
                    label = { Text("Replace") },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.saveEditorFile() },
                    enabled = !state.editorReadOnly
                ) {
                    Text("Save File")
                }
                OutlinedButton(
                    onClick = { viewModel.searchAndReplaceInEditor(searchInput, replaceInput) },
                    enabled = !state.editorReadOnly
                ) {
                    Text("Replace All")
                }
                Text(
                    text = "Lines: $lineCount",
                    color = DevTextSecondary,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }

            OutlinedTextField(
                value = state.editorContent,
                onValueChange = { viewModel.updateEditorContent(it) },
                readOnly = state.editorReadOnly,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                label = { Text("Source Content (${state.editingFile?.extension ?: "txt"})") }
            )
        }
    }
}

// =========================================================================
// H. LOGS VIEWER SCREEN
// =========================================================================
@Composable
fun LogsViewerScreen(state: AppUiState, viewModel: MainViewModel) {
    val components = listOf("ALL", "HTTP", "PHP", "DB", "STORAGE", "SECURITY")
    val filteredLogs = remember(state.logs, state.logFilterComponent) {
        if (state.logFilterComponent == "ALL") state.logs.reversed()
        else state.logs.filter { it.component.equals(state.logFilterComponent, ignoreCase = true) }.reversed()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DevPanelCard(
            title = "Structured Runtime & Security Logs",
            subtitle = "Secrets & passwords automatically redacted"
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (comp in components) {
                    OutlinedButton(onClick = { viewModel.setLogFilter(comp) }) {
                        Text(comp)
                    }
                }
                OutlinedButton(onClick = { viewModel.clearLogs() }) {
                    Text("Clear")
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filteredLogs) { entry ->
                    Text(
                        text = entry.toString(),
                        color = when (entry.level.name) {
                            "ERROR", "SECURITY" -> DevError
                            "WARN" -> DevWarning
                            else -> DevTextSecondary
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

// =========================================================================
// I. NETWORK AND PORTS SCREEN
// =========================================================================
@Composable
fun NetworkPortsScreen(state: AppUiState, viewModel: MainViewModel) {
    var portInput by remember(state.port) { mutableStateOf(state.port.toString()) }
    var lanOptIn by remember(state.lanAccessOptIn) { mutableStateOf(state.lanAccessOptIn) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        DevPanelCard(
            title = "Network Binding & Port Configuration",
            subtitle = "Loopback 127.0.0.1 enforced by default for security"
        ) {
            OutlinedTextField(
                value = portInput,
                onValueChange = { portInput = it },
                label = { Text("HTTP Listening Port (1024 - 65535)") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Switch(checked = lanOptIn, onCheckedChange = { lanOptIn = it })
                Text("Explicitly allow LAN binding (0.0.0.0)", color = DevTextPrimary)
            }
            if (lanOptIn) {
                Text(
                    text = "SECURITY WARNING: Binding to 0.0.0.0 exposes your local development server to other devices on your Wi-Fi/LAN network. Never use on untrusted public networks.",
                    color = DevWarning,
                    fontSize = 12.sp
                )
            }
            Button(onClick = {
                val p = portInput.toIntOrNull() ?: 8080
                viewModel.updateServerPortAndBind(p, lanOptIn)
            }) {
                Text("Apply Port & Network Policy")
            }
        }
    }
}

// =========================================================================
// J. DIAGNOSTICS & VERIFICATION SCREEN
// =========================================================================
@Composable
fun DiagnosticsScreen(state: AppUiState, viewModel: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        DevPanelCard(
            title = "Automated Self-Verification Suite",
            subtitle = "Executes real HTTP, PHP, SQLite, Path Traversal, and Source Protection checks"
        ) {
            Button(onClick = { viewModel.runLiveSelfDiagnostics() }) {
                Text("Execute Live Verification Suite")
            }
            for (rec in state.diagnosticRecords) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DevBackground, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${rec.id} — ${rec.name}", color = DevAccentCyan, fontWeight = FontWeight.Bold)
                        Text(
                            rec.status,
                            color = when (rec.status) {
                                "PASS" -> DevSuccess
                                "FAIL" -> DevError
                                else -> DevWarning
                            },
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text("Procedure: ${rec.procedure}", color = DevTextSecondary, fontSize = 12.sp)
                    Text("Expected: ${rec.expected}", color = DevTextSecondary, fontSize = 12.sp)
                    Text("Actual: ${rec.actual}", color = DevTextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

// =========================================================================
// K. APPLICATION SETTINGS SCREEN
// =========================================================================
@Composable
fun SettingsScreen(state: AppUiState, viewModel: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        DevPanelCard(
            title = "Application Settings & Android Lifecycle Policy",
            subtitle = "Theme: Professional Dark Developer Edition (#080B12)"
        ) {
            KeyValueRow("Application ID", "com.localdev.studiox")
            KeyValueRow("Foreground Service", "Enabled (specialUse Notification)")
            KeyValueRow("Default Loopback", "127.0.0.1:8080")
            KeyValueRow("Storage Scope", "App-Private Scoped Storage (No broad permissions)")
            Text(
                text = "Android OS Lifecycle Note: Android 12+ and OEM battery savers may terminate background processes if the device enters deep Doze mode. LocalDev Studio X uses an explicit Foreground Service with a persistent notification while the HTTP server is RUNNING.",
                color = DevTextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

// =========================================================================
// L. ABOUT AND LICENSES SCREEN
// =========================================================================
@Composable
fun AboutLicensesScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        DevPanelCard(
            title = "About LocalDev Studio X",
            subtitle = "Independent Native Android XAMPP-Inspired Development Environment"
        ) {
            Text(
                text = "LocalDev Studio X (com.localdev.studiox) is an independent open-architecture implementation inspired by the workflow of XAMPP. It is not affiliated with or endorsed by Apache Friends.",
                color = DevTextPrimary,
                fontSize = 13.sp
            )
            KeyValueRow("HTTP Engine", "LocalDev-HttpEngine 1.4.0 (Apache-2.0)")
            KeyValueRow("Database Engine", "SQLite 3 Public Domain + Remote MySQL Wire Probe")
            KeyValueRow("UI Framework", "Android Jetpack Compose Material 3 (Apache-2.0)")
            KeyValueRow("PHP Integration", "PHP CGI/CLI Binary Bridge (PHP License v3.01)")
        }
    }
}
