package com.ywzt.datasync.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "datasync")
public class DatasyncProperties {

    /** 总开关 */
    private boolean enabled = true;

    /** 任务表名，默认 base_data_sync */
    private String tableName = "base_data_sync";

    /** 启动时自动执行 CREATE TABLE IF NOT EXISTS */
    private boolean initTable = true;

    private final Api api = new Api();
    private final Scheduler scheduler = new Scheduler();
    private final Async async = new Async();

    /** 源/目标数据源配置，code=default/空 表示宿主主数据源 */
    private List<Datasource> datasources = new ArrayList<>();

    @Data
    public static class Api {
        /** 是否暴露 /datasync 管理接口 */
        private boolean enabled = true;
        /** 接口根路径 */
        private String path = "/datasync";
    }

    @Data
    public static class Scheduler {
        /** 是否内置定时扫描（不依赖外部调度平台） */
        private boolean enabled = true;
        /** 扫描间隔毫秒，任务自身的 cronExp 决定实际执行时间 */
        private long intervalMs = 30000;
    }

    @Data
    public static class Async {
        private int corePoolSize = 8;
        private int maxPoolSize = 24;
        private int queueCapacity = 100;
    }

    @Data
    public static class Datasource {
        /** 数据源编码，对应任务的 sourceCode/targetCode */
        private String code;
        /** 完整 JDBC URL；为空则由 host/port/database 拼装 MySQL URL（PG 请填 url） */
        private String url;
        private String host;
        private Integer port;
        private String database;
        private String username;
        private String password;
        /** 不填时按 URL 前缀自动识别（mysql/postgresql） */
        private String driver;
        /** 方言：mysql / postgresql / oracle / sqlserver，不填按 URL 或数据库元数据识别 */
        private String dbType;
    }
}
