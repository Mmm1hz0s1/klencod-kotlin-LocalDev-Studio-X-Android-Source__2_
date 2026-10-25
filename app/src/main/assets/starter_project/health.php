<?php
// LocalDev Studio X — JSON Health & Verification Endpoint
header("Content-Type: application/json; charset=utf-8");
header("Cache-Control: no-store");

$token = getenv("LOCALDEV_VERIFICATION_TOKEN") ?: "NONE";
$sqlitePath = getenv("LOCALDEV_SQLITE_PATH") ?: "";

$payload = [
    "status" => "healthy",
    "application" => "LocalDev Studio X",
    "package" => "com.localdev.studiox",
    "php_version" => PHP_VERSION,
    "sapi" => php_sapi_name(),
    "verification_token" => $token,
    "method" => $_SERVER['REQUEST_METHOD'] ?? 'GET',
    "query_echo" => $_GET['echo'] ?? null,
    "sqlite_configured" => !empty($sqlitePath),
    "extensions_count" => count(get_loaded_extensions()),
    "timestamp_utc" => gmdate("Y-m-d\\TH:i:s\\Z")
];

echo json_encode($payload, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES);
