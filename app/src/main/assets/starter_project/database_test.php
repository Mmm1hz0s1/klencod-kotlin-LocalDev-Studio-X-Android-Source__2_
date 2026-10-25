<?php
// LocalDev Studio X — Real PHP-to-Database Connectivity Verification Script (PDO_SQLITE / SQLite3)
header("Content-Type: application/json; charset=utf-8");

$dbPath = getenv("LOCALDEV_SQLITE_PATH");
if (empty($dbPath)) {
    http_response_code(500);
    echo json_encode([
        "success" => false,
        "error" => "LOCALDEV_SQLITE_PATH environment variable is not set."
    ]);
    exit;
}

try {
    $pdo = new PDO("sqlite:" . $dbPath);
    $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);

    $pdo->exec("CREATE TABLE IF NOT EXISTS php_runtime_checks (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        test_key TEXT NOT NULL,
        created_at TEXT NOT NULL
    );");

    $testKey = $_GET['test_key'] ?? ('PHP_DB_PROBE_' . bin2hex(random_bytes(4)));
    $stmt = $pdo->prepare("INSERT INTO php_runtime_checks (test_key, created_at) VALUES (:k, :t)");
    $stmt->execute([
        ':k' => $testKey,
        ':t' => gmdate("Y-m-d H:i:s")
    ]);

    $rowsStmt = $pdo->query("SELECT id, test_key, created_at FROM php_runtime_checks ORDER BY id DESC LIMIT 5");
    $rows = $rowsStmt->fetchAll(PDO::FETCH_ASSOC);

    $verRow = $pdo->query("SELECT sqlite_version() AS ver")->fetch(PDO::FETCH_ASSOC);

    echo json_encode([
        "success" => true,
        "driver" => "pdo_sqlite",
        "sqlite_version" => $verRow['ver'] ?? 'unknown',
        "database_file" => basename($dbPath),
        "inserted_key" => $testKey,
        "recent_rows" => $rows
    ], JSON_PRETTY_PRINT);
} catch (Exception $e) {
    http_response_code(500);
    echo json_encode([
        "success" => false,
        "error" => $e->getMessage()
    ], JSON_PRETTY_PRINT);
}
