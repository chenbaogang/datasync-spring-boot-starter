package com.ywzt.datasync.ds;

import com.ywzt.datasync.config.DatasyncProperties;
import com.zaxxer.hikari.HikariDataSource;
import com.ywzt.datasync.util.DatasyncUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 按编码管理同步用到的连接池；default/0/空 编码直接复用宿主主数据源，
 * 其余编码按 yml 或 SPI 解析后建独立小连接池并缓存。
 */
public class SyncDataSourceManager {

    /** 宿主主数据源在方言缓存中的编码 */
    private static final String HOST = "default";

    private static final Logger log = LoggerFactory.getLogger(SyncDataSourceManager.class);

    private final DataSource hostDataSource;
    private final Map<String, DatasyncProperties.Datasource> configs;
    private final SyncDbLinkResolver resolver;
    private final Map<String, DataSource> pools = new ConcurrentHashMap<>();
    private final Map<String, DbType> dbTypes = new ConcurrentHashMap<>();

    public SyncDataSourceManager(DataSource hostDataSource, List<DatasyncProperties.Datasource> datasources,
                                 SyncDbLinkResolver resolver) {
        this.hostDataSource = hostDataSource;
        this.configs = datasources.stream()
                .collect(Collectors.toMap(DatasyncProperties.Datasource::getCode, d -> d, (a, b) -> a));
        this.resolver = resolver;
    }

    public DataSource resolve(String code) {
        if (isHost(code)) {
            return hostDataSource;
        }
        return pools.computeIfAbsent(code, this::createPool);
    }

    /** SQL 模式来源库的方言，用于生成分页语句 */
    public DbType resolveDbType(String code) {
        return dbTypes.computeIfAbsent(isHost(code) ? HOST : code, this::detectDbType);
    }

    private DbType detectDbType(String code) {
        if (HOST.equals(code)) {
            return byMetaData(hostDataSource, code);
        }
        SyncDbLink link = linkFor(code);
        DbType type = DbType.fromCode(link.getDbType());
        DbType inferred = type == null ? DbType.fromUrl(link.getUrl()) : null;
        if (type == null) {
            type = inferred;
        }
        if (type == null) {
            type = byMetaData(resolve(code), code);
        }
        if (type == null) {
            throw new IllegalArgumentException("无法识别数据源[" + code + "]的数据库类型，"
                    + "请在配置中指定 db-type（mysql/postgresql/oracle/sqlserver）");
        }
        if (inferred != null && inferred != type) {
            log.warn("数据源[{}]配置 db-type={} 与 URL 识别出的 {} 不一致，分页按 {} 生成",
                    code, type.name().toLowerCase(), inferred.name().toLowerCase(), type.name().toLowerCase());
        }
        return type;
    }

    /** 取数据库产品名映射方言，识别不了返回 null */
    private DbType byMetaData(DataSource ds, String code) {
        try (Connection con = ds.getConnection()) {
            return DbType.fromProductName(con.getMetaData().getDatabaseProductName());
        } catch (Exception e) {
            throw new IllegalStateException("数据同步连接数据源[" + code + "]失败: " + e.getMessage(), e);
        }
    }

    private boolean isHost(String code) {
        return DatasyncUtil.isEmpty(code) || "0".equals(code) || "default".equalsIgnoreCase(code);
    }

    /** 先查 SPI，再回落到 yml 配置 */
    private SyncDbLink linkFor(String code) {
        if (resolver != null) {
            SyncDbLink link = resolver.resolve(code);
            if (link != null) {
                return link;
            }
        }
        DatasyncProperties.Datasource cfg = configs.get(code);
        if (cfg == null) {
            throw new IllegalArgumentException("数据同步未找到数据源配置，code=" + code);
        }
        return toLink(cfg);
    }

    private DataSource createPool(String code) {
        SyncDbLink link = linkFor(code);
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("datasync-" + code);
        ds.setJdbcUrl(link.getUrl());
        ds.setUsername(link.getUsername());
        ds.setPassword(link.getPassword());
        ds.setDriverClassName(resolveDriver(link.getDriverClassName(), link.getUrl()));
        ds.setMaximumPoolSize(2);
        ds.setConnectionTimeout(30000);
        return ds;
    }

    private String resolveDriver(String driver, String url) {
        if (DatasyncUtil.isNotEmpty(driver)) {
            return driver;
        }
        if (url != null && url.startsWith("jdbc:postgresql")) {
            return "org.postgresql.Driver";
        }
        // 其余返回 null，由 HikariCP 根据 JDBC URL 自动探测驱动
        return null;
    }

    private SyncDbLink toLink(DatasyncProperties.Datasource cfg) {
        String url = cfg.getUrl();
        if (DatasyncUtil.isEmpty(url)) {
            url = "jdbc:mysql://" + cfg.getHost() + ":" + (cfg.getPort() == null ? 3306 : cfg.getPort())
                    + "/" + cfg.getDatabase() + "?useUnicode=true&characterEncoding=utf8&serverTimezone=GMT%2B8";
        }
        return new SyncDbLink(url, cfg.getUsername(), cfg.getPassword(), cfg.getDriver(), cfg.getDbType());
    }

    public void close() {
        pools.values().forEach(ds -> {
            if (ds instanceof HikariDataSource) {
                ((HikariDataSource) ds).close();
            }
        });
        pools.clear();
    }
}
