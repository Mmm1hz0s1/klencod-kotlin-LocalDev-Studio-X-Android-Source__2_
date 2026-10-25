package com.localdev.studiox.core.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tokenizer for SQL & Code syntax highlighting in the Database SQL Editor and Code Editor.
 */
public final class SqlSyntaxHighlighter {

    public enum TokenType {
        KEYWORD,
        FUNCTION,
        STRING,
        NUMBER,
        COMMENT,
        IDENTIFIER,
        OPERATOR,
        WHITESPACE
    }

    public static final class Token {
        public final TokenType type;
        public final String text;
        public final int start;
        public final int end;

        public Token(TokenType type, String text, int start, int end) {
            this.type = type;
            this.text = text;
            this.start = start;
            this.end = end;
        }
    }

    private static final Set<String> SQL_KEYWORDS = Set.of(
        "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
        "CREATE", "TABLE", "DATABASE", "INDEX", "VIEW", "TRIGGER", "ALTER", "DROP", "ADD",
        "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "NOT", "NULL", "UNIQUE", "DEFAULT",
        "AUTOINCREMENT", "AUTO_INCREMENT", "JOIN", "INNER", "LEFT", "RIGHT",
        "ON", "AS", "ORDER", "BY", "GROUP", "HAVING", "LIMIT", "OFFSET", "AND", "OR",
        "IN", "IS", "LIKE", "BETWEEN", "DISTINCT", "UNION", "ALL", "BEGIN", "TRANSACTION",
        "COMMIT", "ROLLBACK", "PRAGMA", "IF", "EXISTS", "INTEGER", "TEXT", "REAL", "BLOB", "VARCHAR"
    );

    private static final Set<String> SQL_FUNCTIONS = Set.of(
        "COUNT", "SUM", "AVG", "MIN", "MAX", "COALESCE", "IFNULL", "LENGTH", "LOWER", "UPPER",
        "DATETIME", "DATE", "STRFTIME", "SQLITE_VERSION", "ABS", "ROUND"
    );

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
        "(--[^\\n]*)|('(?:''|[^'])*')|(\\b\\d+(?:\\.\\d+)?\\b)|(\\b[A-Za-z_][A-Za-z0-9_]*\\b)|([=<>!+\\-*/(),;]+)|(\\s+)"
    );

    public static List<Token> tokenize(String sql) {
        List<Token> tokens = new ArrayList<>();
        if (sql == null || sql.isEmpty()) return tokens;

        Matcher m = TOKEN_PATTERN.matcher(sql);
        while (m.find()) {
            String chunk = m.group();
            int start = m.start();
            int end = m.end();
            TokenType type;
            if (m.group(1) != null) {
                type = TokenType.COMMENT;
            } else if (m.group(2) != null) {
                type = TokenType.STRING;
            } else if (m.group(3) != null) {
                type = TokenType.NUMBER;
            } else if (m.group(4) != null) {
                String upper = chunk.toUpperCase(Locale.ROOT);
                if (SQL_KEYWORDS.contains(upper)) {
                    type = TokenType.KEYWORD;
                } else if (SQL_FUNCTIONS.contains(upper)) {
                    type = TokenType.FUNCTION;
                } else {
                    type = TokenType.IDENTIFIER;
                }
            } else if (m.group(5) != null) {
                type = TokenType.OPERATOR;
            } else {
                type = TokenType.WHITESPACE;
            }
            tokens.add(new Token(type, chunk, start, end));
        }
        return tokens;
    }
}
