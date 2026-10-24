package com.ywzt.datasync.repo;

import com.ywzt.datasync.util.DatasyncUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * base_data_sync 任务表仓储，纯 JdbcTemplate 实现，兼容 MySQL / PostgreSQL。
 * 标识符不加引号：列名均为小写且经合法性校验，MySQL 与 PG 的未加引号标识符语义一致。
 */
public class SyncTaskRepository {

    private static final Logger log = LoggerFactory.getLogger(SyncTaskRepository.class);

    private static final String[] COLUMNS = {
            "id", "code", "name", "source_code", "target_code", "source_sql", "target_conf", "exe_sql",
            "batch_total", "batch_current", "is_enable", "sync_result", "error_msg", "cron_exp", "next_time",
            "sync_type", "app_key", "app_secret", "sync_url", "uid", "after_policy", "before_policy",
            "body_params", "creator_time"
    };

    private final JdbcTemplate jdbc;
    private final String table;
    private final String selectCols;

    public SyncTaskRepository(JdbcTemplate jdbc, String tableName) {
        if (!tableName.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("非法表名: " + tableName);
        }
        this.jdbc = jdbc;
        this.table = tableName.toLowerCase();
        this.selectCols = String.join(", ", COLUMNS);
    }

    private final RowMapper<SyncTask> mapper = (rs, i) -> {
        SyncTask t = new SyncTask();
        t.setId(rs.getString("id"));
        t.setCode(rs.getString("code"));
        t.setName(rs.getString("name"));
        t.setSourceCode(rs.getString("source_code"));
        t.setTargetCode(rs.getString("target_code"));
        t.setSourceSql(rs.getString("source_sql"));
        t.setTargetConf(rs.getString("target_conf"));
        t.setExeSql(rs.getString("exe_sql"));
        t.setBatchTotal(getLong(rs, "batch_total"));
        t.setBatchCurrent(getLong(rs, "batch_current"));
        t.setIsEnable(rs.getString("is_enable"));
        t.setSyncResult(rs.getString("sync_result"));
        t.setErrorMsg(rs.getString("error_msg"));
        t.setCronExp(rs.getString("cron_exp"));
        Timestamp next = rs.getTimestamp("next_time");
        t.setNextTime(next == null ? null : next.toLocalDateTime());
        int syncType = rs.getInt("sync_type");
        t.setSyncType(rs.wasNull() ? null : syncType);
        t.setAppKey(rs.getString("app_key"));
        t.setAppSecret(rs.getString("app_secret"));
        t.setSyncUrl(rs.getString("sync_url"));
        t.setUid(rs.getString("uid"));
        t.setAfterPolicy(rs.getString("after_policy"));
        t.setBeforePolicy(rs.getString("before_policy"));
        t.setBodyParams(rs.getString("body_params"));
        t.setCreatorTime(rs.getTimestamp("creator_time"));
        return t;
    };

    private static Long getLong(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    /** 启动时按数据库产品自动建表，无法识别的产品只告警不阻断启动 */
    public void ensureTable() {
        String product;
        try {
            product = jdbc.execute((ConnectionCallback<String>) con -> con.getMetaData().getDatabaseProductName());
        } catch (Exception e) {
            log.warn("无法识别数据库产品，跳过自动建表，请手工执行 jar 内 sql 目录的建表脚本", e);
            return;
        }
        if (product == null) {
            return;
        }
        String p = product.toLowerCase();
        if (p.contains("mysql") || p.contains("mariadb")) {
            jdbc.execute("CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "id varchar(50) NOT NULL,"
                    + "code varchar(255) DEFAULT NULL,"
                    + "name varchar(255) DEFAULT NULL,"
                    + "source_code varchar(255) DEFAULT NULL,"
                    + "target_code varchar(255) DEFAULT NULL,"
                    + "source_sql text,"
                    + "target_conf longtext,"
                    + "exe_sql longtext,"
                    + "batch_total bigint DEFAULT NULL,"
                    + "batch_current bigint DEFAULT NULL,"
                    + "is_enable varchar(2) DEFAULT '0',"
                    + "sync_result varchar(2) DEFAULT NULL,"
                    + "error_msg text,"
                    + "cron_exp varchar(255) DEFAULT NULL,"
                    + "next_time datetime DEFAULT NULL,"
                    + "sync_type int DEFAULT 1,"
                    + "app_key varchar(255) DEFAULT NULL,"
                    + "app_secret varchar(255) DEFAULT NULL,"
                    + "sync_url varchar(500) DEFAULT NULL,"
                    + "uid varchar(255) DEFAULT NULL,"
                    + "after_policy varchar(500) DEFAULT NULL,"
                    + "before_policy varchar(500) DEFAULT NULL,"
                    + "body_params text,"
                    + "creator_time datetime DEFAULT NULL,"
                    + "PRIMARY KEY (id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } else if (p.contains("postgres")) {
            jdbc.execute("CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "id varchar(50) NOT NULL PRIMARY KEY,"
                    + "code varchar(255) DEFAULT NULL,"
                    + "name varchar(255) DEFAULT NULL,"
                    + "source_code varchar(255) DEFAULT NULL,"
                    + "target_code varchar(255) DEFAULT NULL,"
                    + "source_sql text,"
                    + "target_conf text,"
                    + "exe_sql text,"
                    + "batch_total bigint DEFAULT NULL,"
                    + "batch_current bigint DEFAULT NULL,"
                    + "is_enable varchar(2) DEFAULT '0',"
                    + "sync_result varchar(2) DEFAULT NULL,"
                    + "error_msg text,"
                    + "cron_exp varchar(255) DEFAULT NULL,"
                    + "next_time timestamp DEFAULT NULL,"
                    + "sync_type int DEFAULT 1,"
                    + "app_key varchar(255) DEFAULT NULL,"
                    + "app_secret varchar(255) DEFAULT NULL,"
                    + "sync_url varchar(500) DEFAULT NULL,"
                    + "uid varchar(255) DEFAULT NULL,"
                    + "after_policy varchar(500) DEFAULT NULL,"
                    + "before_policy varchar(500) DEFAULT NULL,"
                    + "body_params text,"
                    + "creator_time timestamp DEFAULT NULL)");
        } else {
            log.warn("自动建表暂不支持数据库产品[{}]，请手工执行 jar 内 sql 目录的建表脚本", product);
        }
    }

    public List<SyncTask> findPage(String name, long current, long size) {
        StringBuilder sql = new StringBuilder("SELECT " + selectCols + " FROM " + table + " WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (DatasyncUtil.isNotEmpty(name)) {
            sql.append(" AND name LIKE ?");
            args.add("%" + name + "%");
        }
        sql.append(" ORDER BY creator_time DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add((current - 1) * size);
        return jdbc.query(sql.toString(), mapper, args.toArray());
    }

    public long count(String name) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(1) FROM " + table + " WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (DatasyncUtil.isNotEmpty(name)) {
            sql.append(" AND name LIKE ?");
            args.add("%" + name + "%");
        }
        Long total = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    public SyncTask findById(String id) {
        List<SyncTask> list = jdbc.query("SELECT " + selectCols + " FROM " + table + " WHERE id = ?", mapper, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<SyncTask> findEnabled() {
        return jdbc.query("SELECT " + selectCols + " FROM " + table + " WHERE is_enable = '1'", mapper);
    }

    public boolean existsCode(String code) {
        Long c = jdbc.queryForObject("SELECT COUNT(1) FROM " + table + " WHERE code = ?", Long.class, code);
        return c != null && c > 0;
    }

    public void insert(SyncTask t) {
        if (DatasyncUtil.isEmpty(t.getId())) {
            t.setId(DatasyncUtil.uuId());
        }
        if (t.getCreatorTime() == null) {
            t.setCreatorTime(new java.util.Date());
        }
        StringBuilder holders = new StringBuilder();
        for (int i = 0; i < COLUMNS.length; i++) {
            holders.append(i > 0 ? ", ?" : "?");
        }
        jdbc.update("INSERT INTO " + table + " (" + selectCols + ") VALUES (" + holders + ")",
                t.getId(), t.getCode(), t.getName(), t.getSourceCode(), t.getTargetCode(), t.getSourceSql(),
                t.getTargetConf(), t.getExeSql(), t.getBatchTotal(), t.getBatchCurrent(), t.getIsEnable(),
                t.getSyncResult(), t.getErrorMsg(), t.getCronExp(), toTs(t.getNextTime()), t.getSyncType(),
                t.getAppKey(), t.getAppSecret(), t.getSyncUrl(), t.getUid(), t.getAfterPolicy(),
                t.getBeforePolicy(), t.getBodyParams(), t.getCreatorTime());
    }

    /** 仅更新非空字段，等价 mybatis-plus updateById */
    public void updateNonNull(SyncTask t) {
        LinkedHashMap<String, Object> set = new LinkedHashMap<>();
        put(set, "code", t.getCode());
        put(set, "name", t.getName());
        put(set, "source_code", t.getSourceCode());
        put(set, "target_code", t.getTargetCode());
        put(set, "source_sql", t.getSourceSql());
        put(set, "target_conf", t.getTargetConf());
        put(set, "exe_sql", t.getExeSql());
        put(set, "batch_total", t.getBatchTotal());
        put(set, "batch_current", t.getBatchCurrent());
        put(set, "is_enable", t.getIsEnable());
        put(set, "sync_result", t.getSyncResult());
        put(set, "error_msg", t.getErrorMsg());
        put(set, "cron_exp", t.getCronExp());
        put(set, "next_time", toTs(t.getNextTime()));
        put(set, "sync_type", t.getSyncType());
        put(set, "app_key", t.getAppKey());
        put(set, "app_secret", t.getAppSecret());
        put(set, "sync_url", t.getSyncUrl());
        put(set, "uid", t.getUid());
        put(set, "after_policy", t.getAfterPolicy());
        put(set, "before_policy", t.getBeforePolicy());
        put(set, "body_params", t.getBodyParams());
        if (!set.isEmpty()) {
            updateColumnsRaw(set, t.getId());
        }
    }

    /** 按列名批量更新（列名来自内部白名单，不含用户输入） */
    public void updateColumns(String id, Map<String, Object> cols) {
        LinkedHashMap<String, Object> set = new LinkedHashMap<>();
        cols.forEach((k, v) -> put(set, k, v));
        if (!set.isEmpty()) {
            updateColumnsRaw(set, id);
        }
    }

    private void updateColumnsRaw(Map<String, Object> set, String id) {
        StringBuilder sql = new StringBuilder("UPDATE " + table + " SET ");
        List<Object> args = new ArrayList<>();
        boolean first = true;
        for (Map.Entry<String, Object> entry : set.entrySet()) {
            if (!first) {
                sql.append(", ");
            }
            sql.append(entry.getKey()).append(" = ?");
            args.add(entry.getValue());
            first = false;
        }
        sql.append(" WHERE id = ?");
        args.add(id);
        jdbc.update(sql.toString(), args.toArray());
    }

    /** 抢占式声明任务：进行中则失败，多实例下天然互斥 */
    public boolean claim(String id, LocalDateTime nextTime) {
        String sql = "UPDATE " + table
                + " SET sync_result = '1', batch_total = 0, batch_current = 0, error_msg = ''"
                + (nextTime != null ? ", next_time = ?" : "")
                + " WHERE id = ? AND (sync_result IS NULL OR sync_result <> '1')";
        int rows;
        if (nextTime != null) {
            rows = jdbc.update(sql, Timestamp.valueOf(nextTime), id);
        } else {
            rows = jdbc.update(sql, id);
        }
        return rows > 0;
    }

    public void deleteById(String id) {
        jdbc.update("DELETE FROM " + table + " WHERE id = ?", id);
    }

    private static void put(Map<String, Object> map, String col, Object value) {
        if (value != null) {
            map.put(col, value);
        }
    }

    private static Timestamp toTs(LocalDateTime time) {
        return time == null ? null : Timestamp.valueOf(time);
    }
}
