-- datasync-spring-boot-starter 任务表（MySQL）
-- 与 SyncTaskRepository.ensureTable() 自动建表语句一致，供手工执行
CREATE TABLE IF NOT EXISTS `base_data_sync` (
  `id`            varchar(50)  NOT NULL COMMENT '主键',
  `code`          varchar(255) DEFAULT NULL COMMENT '任务编号，不可重复',
  `name`          varchar(255) DEFAULT NULL COMMENT '任务名称',
  `source_code`   varchar(255) DEFAULT NULL COMMENT '来源数据源编码，空/0/default=宿主主库',
  `target_code`   varchar(255) DEFAULT NULL COMMENT '目标数据源编码，空/0/default=宿主主库',
  `source_sql`    text COMMENT '来源SQL，sync_type=1 时必须带 order by',
  `target_conf`   longtext COMMENT '目标配置JSON：[{tableName,batchNum,sysSyncConfItemVoList:[...]}]',
  `exe_sql`       longtext COMMENT '最近一次执行的SQL（调试用）',
  `batch_total`   bigint       DEFAULT NULL COMMENT '总批次',
  `batch_current` bigint       DEFAULT NULL COMMENT '当前批次',
  `is_enable`     varchar(2)   DEFAULT '0' COMMENT '是否开启同步 1:开启 0:关闭',
  `sync_result`   varchar(2)   DEFAULT NULL COMMENT '执行结果 1:进行中 2:成功 3:失败',
  `error_msg`     text COMMENT '异常信息',
  `cron_exp`      varchar(255) DEFAULT NULL COMMENT 'cron表达式',
  `next_time`     datetime     DEFAULT NULL COMMENT '下次执行时间',
  `sync_type`     int          DEFAULT 1 COMMENT '同步类型 1:SQL 2:接口',
  `app_key`       varchar(255) DEFAULT NULL COMMENT '接口模式-开发者账号',
  `app_secret`    varchar(255) DEFAULT NULL COMMENT '接口模式-开发者密码',
  `sync_url`      varchar(500) DEFAULT NULL COMMENT '接口模式-同步URL',
  `uid`           varchar(255) DEFAULT NULL COMMENT '接口模式-数据资产uid',
  `after_policy`  varchar(500) DEFAULT NULL COMMENT '后置处理 bean名.method() 或 全类名.method()',
  `before_policy` varchar(500) DEFAULT NULL COMMENT '前置处理 bean名.method(参数) 或 全类名.method(参数)',
  `body_params`   text COMMENT '接口模式-附加请求体参数JSON',
  `creator_time`  datetime     DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据同步任务';
