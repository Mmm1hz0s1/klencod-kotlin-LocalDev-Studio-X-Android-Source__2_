package com.localdev.studiox.database

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.localdev.studiox.core.engine.DatabaseEngine
import com.localdev.studiox.core.engine.ServiceState
import com.localdev.studiox.core.engine.StructuredLogger
import java.io.File

/**
 * Native Android SQLiteDatabase bridge using Android's built-in libsqlite.so.
 * Works out-of-the-box on every Android device (API 26-35) without external binaries,
 * while sharing the exact same QueryResult & schema inspection model as DatabaseEngine.
 */
class AndroidSqliteBridge(
    private val databaseDir: File,
    private val logger: StructuredLogger
) {
    @Volatile
    var state: ServiceState = ServiceState.STOPPED
        private set

    @Volatile
    var sqliteVersion: String = "SQLite 3 (Android Native)"
        private set

    var activeDbFile: File = File(databaseDir, "localdev_studiox.sqlite3")
        private set

    private val queryHistory = mutableListOf<DatabaseEngine.QueryResult>()

    @Synchronized
    fun start(): Boolean {
        state = ServiceState.STARTING
        return try {
            if (!databaseDir.exists()) databaseDir.mkdirs()
            val res = executeQuery("SELECT sqlite_version() AS version;")
            if (res.success && res.rows.isNotEmpty() && res.rows[0].isNotEmpty()) {
                sqliteVersion = "SQLite ${res.rows[0][0]} (Android Native)"
                state = ServiceState.RUNNING
                logger.info("DB", "Started $sqliteVersion at ${activeDbFile.absolutePath}")
                seedSampleSchemaIfEmpty()
                true
            } else {
                state = ServiceState.ERROR
                logger.error("DB", "Failed to verify Android SQLite version: ${res.errorMessage}")
                false
            }
        } catch (e: Exception) {
            state = ServiceState.ERROR
            logger.error("DB", "Android SQLite startup error: ${e.message}")
            false
        }
    }

    @Synchronized
    fun stop(): Boolean {
        state = ServiceState.STOPPING
        state = ServiceState.STOPPED
        logger.info("DB", "Android SQLite engine stopped.")
        return true
    }

    private fun seedSampleSchemaIfEmpty() {
        val tables = listTables()
        if (tables.isEmpty()) {
            executeQuery(
                """
                CREATE TABLE IF NOT EXISTS dev_projects (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    slug TEXT NOT NULL UNIQUE,
                    title TEXT NOT NULL,
                    created_at TEXT NOT NULL
                );
                """.trimIndent()
            )
            executeQuery(
                """
                INSERT OR IGNORE INTO dev_projects (slug, title, created_at)
                VALUES ('starter', 'Starter Verification Suite', datetime('now'));
                """.trimIndent()
            )
        }
    }

    @Synchronized
    fun executeQuery(sql: String): DatabaseEngine.QueryResult {
        val t0 = System.currentTimeMillis()
        val trimmed = sql.trim()
        if (trimmed.isEmpty()) {
            return DatabaseEngine.QueryResult(false, sql, emptyList(), emptyList(), 0, 0, "SQL statement is empty.")
        }
        var db: SQLiteDatabase? = null
        var cursor: Cursor? = null
        return try {
            if (!databaseDir.exists()) databaseDir.mkdirs()
            db = SQLiteDatabase.openOrCreateDatabase(activeDbFile, null)
            val upper = trimmed.uppercase()
            val isSelectLike = upper.startsWith("SELECT") || upper.startsWith("PRAGMA") || upper.startsWith("EXPLAIN")

            val result = if (isSelectLike) {
                cursor = db.rawQuery(trimmed, null)
                val cols = cursor.columnNames.toList()
                val rows = mutableListOf<List<String>>()
                while (cursor.moveToNext()) {
                    val row = mutableListOf<String>()
                    for (i in cols.indices) {
                        row.add(cursor.getString(i) ?: "NULL")
                    }
                    rows.add(row)
                }
                val elapsed = System.currentTimeMillis() - t0
                DatabaseEngine.QueryResult(true, trimmed, cols, rows, rows.size, elapsed, "")
            } else {
                val statements = trimmed.split(";").map { it.trim() }.filter { it.isNotEmpty() }
                db.beginTransaction()
                try {
                    for (stmt in statements) {
                        db.execSQL(stmt)
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                val elapsed = System.currentTimeMillis() - t0
                DatabaseEngine.QueryResult(
                    true,
                    trimmed,
                    listOf("status", "statements_executed"),
                    listOf(listOf("OK", statements.size.toString())),
                    statements.size,
                    elapsed,
                    ""
                )
            }
            recordHistory(result)
            result
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - t0
            logger.error("DB", "SQL execution failed: ${e.message}")
            val errRes = DatabaseEngine.QueryResult(false, trimmed, emptyList(), emptyList(), 0, elapsed, e.message ?: "Unknown SQL error")
            recordHistory(errRes)
            errRes
        } finally {
            cursor?.close()
            db?.close()
        }
    }

    fun listTables(): List<String> {
        val res = executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' ORDER BY name;")
        return if (res.success) res.rows.mapNotNull { it.firstOrNull() } else emptyList()
    }

    fun inspectTable(tableName: String): DatabaseEngine.QueryResult {
        val safe = tableName.replace(Regex("[^a-zA-Z0-9_]"), "")
        return executeQuery("PRAGMA table_info($safe);")
    }

    fun exportSqlDump(destFile: File): Boolean {
        return try {
            val tables = listTables()
            destFile.bufferedWriter(Charsets.UTF_8).use { fw ->
                fw.write("-- LocalDev Studio X SQLite Dump\n")
                fw.write("-- Engine: $sqliteVersion\n")
                fw.write("BEGIN TRANSACTION;\n\n")
                for (tbl in tables) {
                    val ddl = executeQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name='${tbl.replace("'", "''")}';")
                    if (ddl.success && ddl.rows.isNotEmpty()) {
                        fw.write("DROP TABLE IF EXISTS \"$tbl\";\n")
                        fw.write("${ddl.rows[0][0]};\n")
                    }
                    val data = executeQuery("SELECT * FROM \"$tbl\";")
                    for (row in data.rows) {
                        val vals = row.joinToString(", ") { v ->
                            if (v == "NULL") "NULL" else "'${v.replace("'", "''")}'"
                        }
                        fw.write("INSERT INTO \"$tbl\" VALUES ($vals);\n")
                    }
                    fw.write("\n")
                }
                fw.write("COMMIT;\n")
            }
            logger.info("DB", "Exported SQLite dump to ${destFile.absolutePath}")
            true
        } catch (e: Exception) {
            logger.error("DB", "Export failed: ${e.message}")
            false
        }
    }

    @Synchronized
    fun getHistory(): List<DatabaseEngine.QueryResult> = queryHistory.toList()

    private fun recordHistory(res: DatabaseEngine.QueryResult) {
        queryHistory.add(0, res)
        while (queryHistory.size > 100) {
            queryHistory.removeAt(queryHistory.lastIndex)
        }
    }
}
