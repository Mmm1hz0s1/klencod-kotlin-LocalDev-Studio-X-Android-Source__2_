<?php
// LocalDev Studio X — Primary PHP Runtime Verification Script
header("Content-Type: text/html; charset=utf-8");
header("X-LocalDev-PHP-Engine: " . PHP_VERSION);

session_save_path(getenv("LOCALDEV_SESSION_DIR") ?: sys_get_temp_dir());
@session_start();
if (!isset($_SESSION['visit_count'])) {
    $_SESSION['visit_count'] = 1;
} else {
    $_SESSION['visit_count']++;
}

$verificationToken = getenv("LOCALDEV_VERIFICATION_TOKEN") ?: "STANDALONE-CLI";
$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
$queryParam = $_GET['probe'] ?? 'default';
$postParam = $_POST['form_input'] ?? '';
$computedHash = hash('sha256', $verificationToken . ":" . $queryParam . ":" . $postParam);
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>PHP Runtime Verification — LocalDev Studio X</title>
    <link rel="stylesheet" href="/assets/style.css">
</head>
<body>
    <main class="container">
        <section class="card">
            <span class="badge">PHP INTERPRETER VERIFIED</span>
            <h2>LocalDev Studio X — Dynamic PHP Execution</h2>
            <p>PHP Version: <strong><?php echo htmlspecialchars(PHP_VERSION); ?></strong></p>
            <p>Verification Token: <code id="verify-token"><?php echo htmlspecialchars($verificationToken); ?></code></p>
            <p>Computed Verification Hash: <code id="verify-hash"><?php echo htmlspecialchars($computedHash); ?></code></p>
            <p>HTTP Request Method: <strong><?php echo htmlspecialchars($method); ?></strong></p>
            <p>GET Query Parameter (<code>probe</code>): <strong><?php echo htmlspecialchars($queryParam); ?></strong></p>
            <p>POST Form Parameter (<code>form_input</code>): <strong><?php echo htmlspecialchars($postParam); ?></strong></p>
            <p>Session Visit Counter: <strong><?php echo (int)$_SESSION['visit_count']; ?></strong></p>
        </section>
    </main>
</body>
</html>
