# datasync-spring-boot-starter

普通 Spring Boot 2.x（Java 8+，宿主数据库支持 MySQL / PostgreSQL）项目引入即用。
来源库与目标库可再扩展到 Oracle / SQL Server，见「方言支持」。

## 引入

```xml
<dependency>
    <groupId>com.ywzt</groupId>
    <artifactId>datasync-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

宿主需自带对应 JDBC 驱动。任务表 `base_data_sync` 结构与工程一致，默认启动时按数据库产品自动
`CREATE TABLE IF NOT EXISTS`（MySQL/PG 两套 DDL，`datasync.init-table=false` 可关闭，
也可手工执行 jar 内 `sql/mysql/schema-mysql.sql`、`sql/postgres/schema-postgres.sql`），
已有表的项目可直接平移原数据。

## 配置示例

```yaml
datasync:
  enabled: true              # 总开关
  table-name: base_data_sync # 任务表名
  api:
    enabled: true            # /datasync/** 管理接口
    path: /datasync
  scheduler:
    enabled: true            # 内置扫描器
    interval-ms: 30000
  datasources:               # 源/目标数据源
    - code: erp
      host: 192.168.1.10
      port: 3306
      database: erp_db
      username: root
      password: '******'
    - code: old-mysql
      url: jdbc:mysql://192.168.1.20:3306/old?useSSL=false
      username: root
      password: '******'
    - code: oa-oracle
      url: jdbc:oracle:thin:@//192.168.1.30:1521/orclpdb
      username: scott
      password: '******'
      db-type: oracle        # 可留空，按 URL 前缀识别；URL 也识别不了时才必填
```

`sourceCode`/`targetCode` 为空、`0` 或 `default` 时使用宿主主数据源。
需要动态数据源时可自行注册 `SyncDbLinkResolver` Bean（SPI），返回 `null` 回落 yml 配置。

## 方言支持

任务表 `base_data_sync` 只落在宿主库（MySQL / PG 两套自动 DDL）；来源库与目标库可为下列方言，
方言按 `db-type` → JDBC URL 前缀 → 连接元数据 依次解析，结果按 code 缓存，两者冲突时以 `db-type`
为准并告警。

| db-type | 来源 SQL 模式分页 | 备注 |
|---|---|---|
| mysql | `LIMIT n OFFSET m` | MariaDB 同 |
| postgresql | `LIMIT n OFFSET m` | |
| oracle | `OFFSET m ROWS FETCH NEXT n ROWS ONLY` | 需 12c 及以上，11g 及更早不支持 |
| sqlserver | `OFFSET m ROWS FETCH NEXT n ROWS ONLY` | 需 2012 及以上 |

Oracle / SQL Server 的驱动由宿主项目引入，starter 不传递驱动依赖：

```xml
<dependency>
    <groupId>com.microsoft.sqlserver</groupId>
    <artifactId>mssql-jdbc</artifactId>
    <version>11.2.1.jre8</version>
</dependency>
<dependency>
    <groupId>com.oracle.database.jdbc</groupId>
    <artifactId>ojdbc8</artifactId>
    <version>19.23.0.0</version>
</dependency>
```

标识符大小写：引擎一律使用**未加引号**的表名/列名，PG 与 MySQL 折叠为小写、
Oracle 折叠为大写、SQL Server 不区分大小写。因此目标表请以不加引号的方式建列，不要创建带引号的
混合大小写对象，否则需要同名折叠才能命中。

目标写入是逐行参数化 INSERT/UPDATE，语句本身各方言通用。

## 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /datasync/page?name=&currentPage=&pageSize= | 分页查询任务 |
| GET | /datasync/{id} | 任务详情 |
| POST | /datasync/save | 新增任务（校验 code 唯一、SQL 模式必须带 order by） |
| POST | /datasync/updateById | 更新任务 |
| GET | /datasync/deleteById/{id} | 删除任务 |
| GET | /datasync/dataSync/{id} | 手动触发一次同步（异步） |
| GET | /datasync/scan | 手动执行一轮调度扫描 |

也可注入 `DatasyncManager` 以编程方式管理任务（`save/updateById/trigger/runNow`）。

## 实现说明

- 同步引擎：SQL 分页 / 接口（appKey+appSecret SHA1 签名）两种来源，
  字典映射、日期格式转换、`${表.字段}` 跨表引用、前/后置策略反射调用。
- 目标写入参数化 JDBC，多实例并发由
  数据库抢占声明（`UPDATE ... WHERE sync_result <> '1'`）互斥，不依赖 lock4j/Redis。
- 接口模式来源分页大小沿用固定 1000；SQL 模式分页取 target_conf 第一张表的 batchNum。
- SQL 模式来源分页按来源库方言生成（MySQL/PG 用 LIMIT/OFFSET，Oracle/SQL Server 用
  OFFSET..FETCH NEXT），接口模式与目标写入不涉及方言。
- 扫描调度内置，无需 xxl-job；
- 注意：任务异常中断会停留在 `sync_result=1`（进行中），需人工重置后再调度。
