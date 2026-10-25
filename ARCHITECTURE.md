# Technical Architecture — LocalDev Studio X (`com.localdev.studiox`)

## 1. Architectural Pattern & Separation of Responsibilities

LocalDev Studio X follows a strict **MVVM + Clean Engine Separation** architecture:

- **`core/engine/` (Pure Java 17 Zero-Dependency Runtime Layer)**:
  - `ServiceState.java`: Explicit state machine (`STOPPED`, `STARTING`, `RUNNING`, `STOPPING`, `ERROR`).
  - `LocalHttpServerEngine.java`: Multi-threaded HTTP/1.1 socket server with pre-bind port-conflict detection, self-health check verification (`OPTIONS / HTTP/1.1`), MIME resolver, and CGI request bridge.
  - `PathSecurityValidator.java`: Canonical path confinement, double-URL-decode traversal prevention (`..`, `%2e%2e`, `%252e%252e`), dotfile blocking, and sensitive extension filtering.
  - `PhpRuntimeConfig.java` & `PhpRuntimeEngine.java`: `php.ini` generator, process timeout enforcer, CGI header/body parser, and raw PHP source disclosure prevention.
  - `DatabaseEngine.java`: SQLite 3 query/script runner, schema inspector, `.sql` dump exporter/importer, and Remote MySQL/MariaDB TCP V10 handshake inspector.
  - `SqlSyntaxHighlighter.java`: Lexical tokenizer for SQL keywords, functions, strings, numbers, comments, and operators.
  - `StructuredLogger.java`: Thread-safe bounded ring logger with automatic credential/token redaction.
- **`database/AndroidSqliteBridge.kt`**:
  - Direct integration with Android's native `android.database.sqlite.SQLiteDatabase` (`libsqlite.so`), ensuring zero-config local SQLite 3 functionality on every Android device (API 26–35).
- **`storage/WorkspaceManager.kt`**:
  - Manages app-private scoped storage (`files/projects/htdocs/`, `files/runtime_config/`, `files/databases/`, `files/exports/`), deploys the starter verification project from APK assets, and exports project ZIP archives.
- **`service/LocalDevForegroundService.kt`**:
  - Android 8.0–15 compliant Foreground Service (`FOREGROUND_SERVICE_TYPE_SPECIAL_USE`) displaying an ongoing notification with quick Stop action while the HTTP server is active.
- **`ui/` (Jetpack Compose Material 3 Dark Developer Edition)**:
  - `MainViewModel.kt`: Lifecycle-aware `AndroidViewModel` coordinating coroutines on `Dispatchers.IO` and exposing immutable `StateFlow<AppUiState>`.
  - `LocalDevScreens.kt`: All 12 developer screens built with Material 3 components.

## 2. Server, PHP, Database & phpMyAdmin Feasibility Evaluation

1. **HTTP Server Evaluation (Apache vs Native HTTP Engine)**:
   - Apache `httpd` relies on Multi-Processing Modules (`mpm_prefork`/`mpm_worker`), SysV IPC semaphores, and root-style config hierarchies that are restricted inside unprivileged Android SELinux sandboxes.
   - Therefore, LocalDev Studio X implements **`LocalHttpServerEngine` v1.4.0** and explicitly identifies it by its true engine name rather than misrepresenting it as Apache.
2. **Database Evaluation (MySQL / MariaDB vs SQLite 3 + Remote MySQL)**:
   - Desktop MySQL/MariaDB daemons (`mysqld`/`mariadbd`) require POSIX shared memory, specific user/group UIDs, and large background memory footprints that Android's Phantom Process Killer (Android 12+) terminates aggressively.
   - LocalDev Studio X provides **SQLite 3** (`AndroidSqliteBridge` / `DatabaseEngine`) as the verified local database engine—clearly labeled as **SQLite 3**—and provides a separate **Remote MySQL / MariaDB TCP Connection Inspector** for external MySQL servers.
3. **Database Web Administration (phpMyAdmin vs Native Compose Admin)**:
   - Because phpMyAdmin requires a full MySQL/MariaDB server with `mysqli`/`pdo_mysql` and `information_schema` tables, embedding a fake phpMyAdmin page over SQLite would violate engineering integrity.
   - LocalDev Studio X implements a **complete Native Jetpack Compose Database Manager & SQL Editor** supporting syntax highlighting, table schema inspection, `CREATE/SELECT/INSERT/UPDATE/DELETE/ALTER/DROP`, query history, and `.sql` dump import/export.
