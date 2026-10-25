# LocalDev Studio X — Starter Verification Project

This starter workspace is automatically deployed to your application-private web root (`files/projects/htdocs/starter/`) upon first launch of **LocalDev Studio X** (`com.localdev.studiox`).

## Included Files
- `index.html`: Static HTML5 landing page verifying static asset delivery, MIME headers, CSS, and JavaScript.
- `index.php`: Dynamic PHP verification script demonstrating `GET` parameters, `POST` form payloads, PHP session persistence, and cryptographic token verification (`LOCALDEV_VERIFICATION_TOKEN`).
- `health.php`: JSON health-check endpoint returning the active PHP version, loaded extension count, SAPI name, and verification token.
- `database_test.php`: Real PDO SQLite database integration script that creates a table (`php_runtime_checks`), inserts a timestamped record, and queries the persisted rows back as JSON.
- `assets/style.css`: Dark Developer Edition stylesheet matching the native Jetpack Compose Material 3 palette (`#080B12`, `#101722`, `#151F2D`, `#38BDF8`).
- `assets/app.js`: Client-side script verifying `application/javascript` delivery.
