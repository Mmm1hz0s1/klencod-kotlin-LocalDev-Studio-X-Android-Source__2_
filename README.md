# LocalDev Studio X (`com.localdev.studiox`)

**Native Android XAMPP-Inspired Local Web Development Environment**

> **Disclaimer**: LocalDev Studio X is an independent native Android implementation inspired by the workflow of XAMPP. It is not affiliated with, endorsed by, or representing the official Apache Friends XAMPP distribution.

## Overview

**LocalDev Studio X** provides a native Android local web-development stack built with **Kotlin**, **Java 17 Core Runtime Engines**, and **Jetpack Compose (Material 3)** using the **Professional Dark Developer Edition** color system (`#080B12` background, `#101722` primary surface, `#151F2D` secondary surface, `#38BDF8` cyan accent, `#3B82F6` blue accent).

### Actual Engineered Stack Components

| Component | Actual Implementation | Version / Target | Status |
| :--- | :--- | :--- | :--- |
| **HTTP Server Engine** | `LocalHttpServerEngine` (Native Socket HTTP/1.1 Server) | `1.4.0` | **Implemented & Live-Verified** |
| **PHP Runtime Engine** | `PhpRuntimeEngine` (CGI/1.1 & CLI Process Bridge for `libphp_cgi.so`) | PHP 8.x ABI Bridge | **Bridge Implemented & Verified (Requires ABI binary `libphp_cgi.so` in `jniLibs`)** |
| **Local SQL Database** | **SQLite 3** (`AndroidSqliteBridge` + `DatabaseEngine`) | SQLite `3.40.1` / Android Native | **Implemented & Live-Verified** |
| **Remote MySQL / MariaDB** | `DatabaseEngine.testRemoteMySqlHandshake` (TCP V10 Wire Inspector) | MySQL / MariaDB Protocol v10 | **Implemented & Verified** |
| **Database Web Admin** | Native Compose **Database Manager & SQL Editor** (phpMyAdmin alternative) | Built-in Native UI | **Implemented & Verified** |
| **Android Foreground Service** | `LocalDevForegroundService` (`specialUse` Foreground Notification) | Android 8.0 – 15 (API 26–35) | **Implemented** |

## Key Features

1. **XAMPP-Inspired Server Control Panel**: Explicit state machine (`STOPPED`, `STARTING`, `RUNNING`, `STOPPING`, `ERROR`) where `RUNNING` is only set after a real socket self-health check (`OPTIONS / HTTP/1.1`) succeeds.
2. **Real HTTP Server (`LocalHttpServerEngine`)**:
   - Defaults to `http://127.0.0.1:8080/` with configurable unprivileged ports (`1024–65535`).
   - Serves static HTML, CSS, JavaScript, JSON, SVG, images, and web fonts with accurate MIME headers.
   - Strict `PathSecurityValidator` preventing directory traversal (`../`, `%2e%2e`, double-encoded `%252e%252e`), dotfile access (`.env`, `.git`), and sensitive config disclosure (`php.ini`, `.sqlite3`).
   - Strict PHP source protection: `.php` files are never served as static text even if the PHP binary is stopped or missing (returns `503 Service Unavailable`).
3. **PHP Runtime Bridge (`PhpRuntimeEngine`)**:
   - Executes `.php` scripts via CGI/1.1 environment variables (`REQUEST_METHOD`, `QUERY_STRING`, `CONTENT_LENGTH`, `CONTENT_TYPE`, `SCRIPT_FILENAME`, `DOCUMENT_ROOT`, `REDIRECT_STATUS=200`).
   - Generates `php.ini` outside the public web root (`files/runtime_config/php.ini`).
   - Enforces configurable execution timeouts and restricts `phpinfo()` to an explicit local-only diagnostic action.
4. **SQL Database Engine & SQL Editor (`DatabaseEngine` & `AndroidSqliteBridge`)**:
   - Clearly labeled **SQLite 3** local embedded database engine (never misrepresented as MySQL).
   - Full support for `CREATE`, `SELECT`, `INSERT`, `UPDATE`, `DELETE`, `ALTER`, `DROP`, and `PRAGMA` statements.
   - Syntax-highlighted SQL editor (`SqlSyntaxHighlighter`), schema inspector, query history, and `.sql` dump import/export.
   - Dedicated **Remote MySQL / MariaDB** TCP connection inspector.
5. **12 Native Jetpack Compose Screens**:
   - Dashboard, Server Control Panel, PHP Runtime, Database & SQL Editor, Website Projects, File Explorer, Code & Configuration Editor, Logs Viewer, Network & Ports, Diagnostics & Verification, Application Settings, and About & Licenses.
