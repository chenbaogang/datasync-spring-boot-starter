package com.ywzt.datasync.ds;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DbTypeTest {

    @Test
    void pageSqlUsesLimitOffsetForMysqlAndPg() {
        assertEquals("select id from t ORDER BY id LIMIT 1000 OFFSET 2000",
                DbType.MYSQL.pageSql("select id from t", "id", 2000, 1000));
        assertEquals("select id from t ORDER BY id LIMIT 1000 OFFSET 2000",
                DbType.POSTGRESQL.pageSql("select id from t", "id", 2000, 1000));
    }

    @Test
    void pageSqlUsesAnsiOffsetFetchForOracleAndSqlServer() {
        assertEquals("select id from t ORDER BY id DESC OFFSET 2000 ROWS FETCH NEXT 1000 ROWS ONLY",
                DbType.ORACLE.pageSql("select id from t", "id DESC", 2000, 1000));
        assertEquals("select id from t ORDER BY id OFFSET 2000 ROWS FETCH NEXT 1000 ROWS ONLY",
                DbType.SQLSERVER.pageSql("select id from t", "id", 2000, 1000));
    }

    @Test
    void resolvesFromCodeUrlAndProductName() {
        assertEquals(DbType.ORACLE, DbType.fromCode("Oracle"));
        assertEquals(DbType.SQLSERVER, DbType.fromCode("mssql"));
        assertEquals(DbType.POSTGRESQL, DbType.fromCode("pg"));
        assertNull(DbType.fromCode(null));

        assertEquals(DbType.SQLSERVER, DbType.fromUrl("jdbc:sqlserver://1.1.1.1:1433;db=test"));
        assertEquals(DbType.ORACLE, DbType.fromUrl("jdbc:oracle:thin:@//h:1521/orcl"));
        assertNull(DbType.fromUrl("jdbc:h2:mem:test"));

        assertEquals(DbType.ORACLE, DbType.fromProductName("Oracle"));
        assertEquals(DbType.SQLSERVER, DbType.fromProductName("Microsoft SQL Server"));
        assertEquals(DbType.MYSQL, DbType.fromProductName("MySQL"));
        assertEquals(DbType.POSTGRESQL, DbType.fromProductName("PostgreSQL"));
    }

    @Test
    void unknownCodeRejected() {
        assertThrows(IllegalArgumentException.class, () -> DbType.fromCode("db2"));
    }
}
