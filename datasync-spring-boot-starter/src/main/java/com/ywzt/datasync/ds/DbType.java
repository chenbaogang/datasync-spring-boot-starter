package com.ywzt.datasync.ds;

import com.ywzt.datasync.util.DatasyncUtil;

/**
 * 数据库方言，目前仅用于 SQL 模式来源的分页读取。
 * 目标表写入用的是通用 SQL，不受方言影响。
 */
public enum DbType {

    /** MySQL / MariaDB */
    MYSQL,
    /** PostgreSQL */
    POSTGRESQL,
    /** Oracle 12c 及以上 */
    ORACLE,
    /** SQL Server 2012 及以上 */
    SQLSERVER;

    /** 生成分页查询 SQL，orderBy 为已通过合法性校验的排序片段 */
    public String pageSql(String baseSql, String orderBy, long offset, long limit) {
        if (this == ORACLE || this == SQLSERVER) {
            return baseSql + " ORDER BY " + orderBy
                    + " OFFSET " + offset + " ROWS FETCH NEXT " + limit + " ROWS ONLY";
        }
        return baseSql + " ORDER BY " + orderBy + " LIMIT " + limit + " OFFSET " + offset;
    }

    /** 解析配置的 db-type：mysql / postgresql(pg) / oracle / sqlserver(mssql) */
    public static DbType fromCode(String code) {
        if (DatasyncUtil.isEmpty(code)) {
            return null;
        }
        String c = code.trim().toLowerCase();
        if (c.startsWith("mysql") || c.startsWith("mariadb")) {
            return MYSQL;
        }
        if (c.startsWith("postgre") || "pg".equals(c)) {
            return POSTGRESQL;
        }
        if (c.startsWith("oracle")) {
            return ORACLE;
        }
        if (c.startsWith("sqlserver") || "mssql".equals(c)) {
            return SQLSERVER;
        }
        throw new IllegalArgumentException("不支持的 db-type: " + code);
    }

    /** 按 JDBC URL 前缀推断，识别不了返回 null */
    public static DbType fromUrl(String url) {
        if (DatasyncUtil.isEmpty(url)) {
            return null;
        }
        String u = url.trim().toLowerCase();
        if (u.startsWith("jdbc:mysql") || u.startsWith("jdbc:mariadb")) {
            return MYSQL;
        }
        if (u.startsWith("jdbc:postgresql")) {
            return POSTGRESQL;
        }
        if (u.startsWith("jdbc:oracle")) {
            return ORACLE;
        }
        if (u.contains("sqlserver")) {
            return SQLSERVER;
        }
        return null;
    }

    /** 按 JDBC 元数据产品名推断，识别不了返回 null */
    public static DbType fromProductName(String product) {
        if (DatasyncUtil.isEmpty(product)) {
            return null;
        }
        String p = product.trim().toLowerCase();
        if (p.contains("mysql") || p.contains("mariadb")) {
            return MYSQL;
        }
        if (p.contains("postgres")) {
            return POSTGRESQL;
        }
        if (p.contains("oracle")) {
            return ORACLE;
        }
        if (p.contains("sql server")) {
            return SQLSERVER;
        }
        return null;
    }
}
