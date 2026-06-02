package com.campus.activity.service;

import com.campus.activity.common.BusinessException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AdminSqlValidator {
    private static final int MAX_LIMIT = 50;
    private static final Pattern TABLE_PATTERN = Pattern.compile("(?i)\\b(?:from|join)\\s+`?([a-zA-Z_][a-zA-Z0-9_]*)`?");
    private static final Pattern LIMIT_PATTERN = Pattern.compile("(?i)\\blimit\\s+(\\d+)\\b");
    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "TRUNCATE", "CALL", "OUTFILE",
            "LOAD", "GRANT", "REVOKE", "CREATE", "REPLACE"
    );

    private final QueryMetadataRegistry metadataRegistry;

    public AdminSqlValidator(QueryMetadataRegistry metadataRegistry) {
        this.metadataRegistry = metadataRegistry;
    }

    public String validateAndLimit(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new BusinessException(40002, "管理员 SQL 草稿不能为空");
        }
        String normalized = sql.strip();
        rejectEscapeSyntax(normalized);
        rejectForbiddenKeyword(normalized);
        rejectSensitiveFields(normalized);
        parseSelect(normalized);
        validateTables(normalized);
        return enforceLimit(normalized);
    }

    private void rejectEscapeSyntax(String sql) {
        if (sql.contains(";") || sql.contains("--") || sql.contains("/*") || sql.contains("*/")) {
            throw new BusinessException(40002, "管理员 SQL 草稿不允许多语句、分号或注释");
        }
    }

    private void rejectForbiddenKeyword(String sql) {
        String upper = sql.toUpperCase(Locale.ROOT);
        if (!upper.stripLeading().startsWith("SELECT")) {
            throw new BusinessException(40002, "管理员 SQL 草稿只允许 SELECT");
        }
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (upper.matches("(?s).*\\b" + keyword + "\\b.*")) {
                throw new BusinessException(40002, "管理员 SQL 草稿包含禁止操作：" + keyword);
            }
        }
    }

    private void rejectSensitiveFields(String sql) {
        if (metadataRegistry.containsSensitiveToken(sql)) {
            throw new BusinessException(40002, "管理员 SQL 草稿包含敏感字段");
        }
    }

    private void parseSelect(String sql) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (!(statement instanceof Select)) {
                throw new BusinessException(40002, "管理员 SQL 草稿只允许单条 SELECT");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(40002, "管理员 SQL 草稿解析失败：" + ex.getMessage());
        }
    }

    private void validateTables(String sql) {
        Matcher matcher = TABLE_PATTERN.matcher(sql);
        boolean found = false;
        while (matcher.find()) {
            found = true;
            String table = matcher.group(1);
            if (!metadataRegistry.isAllowedTable(table)) {
                throw new BusinessException(40002, "管理员 SQL 草稿包含未知表：" + table);
            }
        }
        if (!found) {
            throw new BusinessException(40002, "管理员 SQL 草稿缺少业务表");
        }
    }

    private String enforceLimit(String sql) {
        Matcher matcher = LIMIT_PATTERN.matcher(sql);
        if (!matcher.find()) {
            return sql + " LIMIT " + MAX_LIMIT;
        }
        int limit = Integer.parseInt(matcher.group(1));
        if (limit <= MAX_LIMIT) {
            return sql;
        }
        return matcher.replaceFirst("LIMIT " + MAX_LIMIT);
    }
}
