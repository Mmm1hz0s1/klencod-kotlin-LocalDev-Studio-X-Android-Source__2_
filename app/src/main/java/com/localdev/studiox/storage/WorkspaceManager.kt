package com.localdev.studiox.storage

import android.content.Context
import com.localdev.studiox.core.engine.StructuredLogger
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Manages the application-private web root (`files/projects/htdocs/`), starter project deployment,
 * project creation, file reading/saving, and ZIP archive export.
 */
class WorkspaceManager(
    private val context: Context,
    private val logger: StructuredLogger
) {
    val projectsRootDir: File = File(context.filesDir, "projects/htdocs")
    val runtimeConfigDir: File = File(context.filesDir, "runtime_config")
    val phpSessionsDir: File = File(context.filesDir, "php_sessions")
    val databasesDir: File = File(context.filesDir, "databases")
    val exportsDir: File = File(context.filesDir, "exports")

    fun ensureInitialized(): File {
        projectsRootDir.mkdirs()
        runtimeConfigDir.mkdirs()
        phpSessionsDir.mkdirs()
        databasesDir.mkdirs()
        exportsDir.mkdirs()

        val starterDir = File(projectsRootDir, "starter")
        if (!starterDir.exists() || starterDir.listFiles().isNullOrEmpty()) {
            deployStarterProjectFromAssets(starterDir)
        }
        return starterDir
    }

    private fun deployStarterProjectFromAssets(targetDir: File) {
        targetDir.mkdirs()
        val assetFiles = listOf(
            "index.html",
            "index.php",
            "health.php",
            "database_test.php",
            "README.md",
            "assets/style.css",
            "assets/app.js"
        )
        for (rel in assetFiles) {
            try {
                val outFile = File(targetDir, rel)
                outFile.parentFile?.mkdirs()
                context.assets.open("starter_project/$rel").use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                logger.warn("STORAGE", "Failed to copy starter asset $rel: ${e.message}")
            }
        }
        logger.info("STORAGE", "Deployed starter verification project to ${targetDir.absolutePath}")
    }

    fun listProjects(): List<File> {
        if (!projectsRootDir.exists()) return emptyList()
        return projectsRootDir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
    }

    fun createNewProject(projectName: String): Result<File> {
        val cleanName = projectName.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")
        if (cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Project name must contain alphanumeric characters."))
        }
        val projDir = File(projectsRootDir, cleanName)
        if (projDir.exists()) {
            return Result.failure(IllegalStateException("Project '$cleanName' already exists."))
        }
        projDir.mkdirs()
        File(projDir, "index.html").writeText(
            """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>$cleanName — LocalDev Studio X</title>
                <style>
                    body { background:#080B12; color:#F8FAFC; font-family:monospace; padding:32px; }
                    .box { background:#101722; border:1px solid #38BDF8; padding:20px; border-radius:8px; }
                </style>
            </head>
            <body>
                <div class="box">
                    <h1>Project: $cleanName</h1>
                    <p>Created in LocalDev Studio X at <code>htdocs/$cleanName/</code></p>
                </div>
            </body>
            </html>
            """.trimIndent()
        )
        File(projDir, "index.php").writeText(
            """
            <?php
            header("Content-Type: text/html; charset=utf-8");
            echo "<h1>PHP Project: " . htmlspecialchars('$cleanName') . "</h1>";
            echo "<p>Executed by PHP " . PHP_VERSION . "</p>";
            """.trimIndent()
        )
        logger.info("STORAGE", "Created new project '$cleanName' at ${projDir.absolutePath}")
        return Result.success(projDir)
    }

    fun listFilesInDirectory(dir: File): List<File> {
        if (!isInsideProjectsRoot(dir)) return emptyList()
        return dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
    }

    fun isInsideProjectsRoot(target: File): Boolean {
        return try {
            val rootCanon = projectsRootDir.canonicalPath
            val targetCanon = target.canonicalPath
            targetCanon == rootCanon || targetCanon.startsWith(rootCanon + File.separator)
        } catch (e: Exception) {
            false
        }
    }

    fun exportProjectAsZip(projectDir: File): Result<File> {
        if (!isInsideProjectsRoot(projectDir) || !projectDir.isDirectory) {
            return Result.failure(SecurityException("Project directory is outside authorized web root."))
        }
        return try {
            val zipFile = File(exportsDir, "${projectDir.name}-export.zip")
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                projectDir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val relPath = file.relativeTo(projectDir).path.replace("\\", "/")
                    zos.putNextEntry(ZipEntry("${projectDir.name}/$relPath"))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            logger.info("STORAGE", "Exported project archive ${zipFile.name} (${zipFile.length()} bytes)")
            Result.success(zipFile)
        } catch (e: Exception) {
            logger.error("STORAGE", "Project ZIP export failed: ${e.message}")
            Result.failure(e)
        }
    }
}
