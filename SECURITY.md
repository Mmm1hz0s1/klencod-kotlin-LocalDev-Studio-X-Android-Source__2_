# Security Baseline — LocalDev Studio X

LocalDev Studio X is engineered as a **local mobile web-development environment**, not a public production hosting server.

## Enforced Security Controls

1. **Loopback Binding by Default**: `LocalHttpServerEngine` binds strictly to `127.0.0.1` by default. Exposing the server to LAN (`0.0.0.0`) requires an explicit developer opt-in toggle in the **Network & Ports** screen and displays an immediate warning banner.
2. **Directory Traversal & Path Confinement**: `PathSecurityValidator` canonicalizes both the configured `documentRoot` and the requested target path, performs double URL decoding to catch `%252e%252e` bypasses, blocks `..` segments, and verifies `target.getCanonicalPath().startsWith(docRoot.getCanonicalPath() + File.separator)`.
3. **Zero Raw PHP Source Code Disclosure**: Requests for `.php` files are never routed to the static file reader. If the PHP runtime is stopped or in an `ERROR` state, the server returns `HTTP 503 Service Unavailable` with a safe diagnostic message and never leaks `<?php` source code.
4. **Configuration & Secret Isolation**:
   - Generated `php.ini` files are stored in `files/runtime_config/php.ini`, completely outside `files/projects/htdocs/`.
   - `PathSecurityValidator` blocks HTTP access to dotfiles (`.env`, `.git`, `.htaccess`) and sensitive extensions (`.ini`, `.sqlite`, `.sqlite3`, `.db`, `.key`, `.pem`, `.conf`, `.sh`).
   - `StructuredLogger` automatically scrubs `password=`, `passwd=`, `secret=`, and `token=` patterns from log entries.
5. **Restricted `phpinfo()` Diagnostics**: `phpinfo()` is never exposed as a default public web page; it can only be invoked locally inside the app when explicitly enabled in `PhpRuntimeConfig`.
