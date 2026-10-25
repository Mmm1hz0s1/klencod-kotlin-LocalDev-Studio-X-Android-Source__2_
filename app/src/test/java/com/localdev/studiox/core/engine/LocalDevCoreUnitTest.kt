package com.localdev.studiox.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LocalDevCoreUnitTest {

    @Test
    fun testPathSecurityBlocksTraversalAndDotfiles() {
        val tempDocRoot = Files.createTempDirectory("ldx_docroot").toFile()
        File(tempDocRoot, "index.html").writeText("<h1>OK</h1>")
        File(tempDocRoot, ".env").writeText("SECRET=1")

        val okRes = PathSecurityValidator.validateRequestPath(tempDocRoot, "/index.html")
        assertTrue(okRes.allowed)
        assertEquals(200, okRes.httpStatus)

        val travRes = PathSecurityValidator.validateRequestPath(tempDocRoot, "/../../etc/passwd")
        assertFalse(travRes.allowed)
        assertEquals(403, travRes.httpStatus)

        val doubleEncodedRes = PathSecurityValidator.validateRequestPath(tempDocRoot, "/%252e%252e/etc/passwd")
        assertFalse(doubleEncodedRes.allowed)
        assertEquals(403, doubleEncodedRes.httpStatus)

        val envRes = PathSecurityValidator.validateRequestPath(tempDocRoot, "/.env")
        assertFalse(envRes.allowed)
        assertEquals(403, envRes.httpStatus)

        tempDocRoot.deleteRecursively()
    }

    @Test
    fun testSqlSyntaxHighlighterTokenizesKeywordsAndNumbers() {
        val tokens = SqlSyntaxHighlighter.tokenize("SELECT id, name FROM users WHERE active = 1 -- filter")
        assertTrue(tokens.isNotEmpty())
        assertEquals(SqlSyntaxHighlighter.TokenType.KEYWORD, tokens.first().type)
        assertTrue(tokens.any { it.type == SqlSyntaxHighlighter.TokenType.COMMENT })
        assertTrue(tokens.any { it.type == SqlSyntaxHighlighter.TokenType.NUMBER && it.text == "1" })
    }

    @Test
    fun testStructuredLoggerRedactsPasswordsAndTokens() {
        val logger = StructuredLogger(100)
        logger.info("DB", "Connecting user=root password=mySecretPassword123 host=127.0.0.1")
        val entry = logger.entries.first()
        assertFalse(entry.message.contains("mySecretPassword123"))
        assertTrue(entry.message.contains("REDACTED"))
    }
}
