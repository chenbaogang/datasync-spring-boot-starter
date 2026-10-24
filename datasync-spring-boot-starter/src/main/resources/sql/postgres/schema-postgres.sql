-- datasync-spring-boot-starter 任务表（PostgreSQL）
-- 与 SyncTaskRepository.ensureTable() 自动建表语句一致，供手工执行
CREATE TABLE IF NOT EXISTS base_data_sync (
  id            varchar(50)  NOT NULL PRIMARY KEY,
  code          varchar(255) DEFAULT NULL,
  name          varchar(255) DEFAULT NULL,
  source_code   varchar(255) DEFAULT NULL,
  target_code   varchar(255) DEFAULT NULL,
  source_sql    text,
  target_conf   text,
  exe_sql       text,
  batch_total   bigint       DEFAULT NULL,
  batch_current bigint       DEFAULT NULL,
  is_enable     varchar(2)   DEFAULT '0',
  sync_result   varchar(2)   DEFAULT NULL,
  error_msg     text,
  cron_exp      varchar(255) DEFAULT NULL,
  next_time     timestamp    DEFAULT NULL,
  sync_type     int          DEFAULT 1,
  app_key       varchar(255) DEFAULT NULL,
  app_secret    varchar(255) DEFAULT NULL,
  sync_url      varchar(500) DEFAULT NULL,
  uid           varchar(255) DEFAULT NULL,
  after_policy  varchar(500) DEFAULT NULL,
  before_policy varchar(500) DEFAULT NULL,
  body_params   text,
  creator_time  timestamp    DEFAULT NULL
);
COMMENT ON TABLE base_data_sync IS '数据同步任务';
COMMENT ON COLUMN base_data_sync.id IS '主键';
COMMENT ON COLUMN base_data_sync.code IS '任务编号，不可重复';
COMMENT ON COLUMN base_data_sync.name IS '任务名称';
COMMENT ON COLUMN base_data_sync.source_code IS '来源数据源编码，空/0/default=宿主主库';
COMMENT ON COLUMN base_data_sync.target_code IS '目标数据源编码，空/0/default=宿主主库';
COMMENT ON COLUMN base_data_sync.source_sql IS '来源SQL，sync_type=1 时必须带 order by';
COMMENT ON COLUMN base_data_sync.target_conf IS '目标配置JSON：[{tableName,batchNum,sysSyncConfItemVoList:[...]}]';
COMMENT ON COLUMN base_data_sync.exe_sql IS '最近一次执行的SQL（调试用）';
COMMENT ON COLUMN base_data_sync.batch_total IS '总批次';
COMMENT ON COLUMN base_data_sync.batch_current IS '当前批次';
COMMENT ON COLUMN base_data_sync.is_enable IS '是否开启同步 1:开启 0:关闭';
COMMENT ON COLUMN base_data_sync.sync_result IS '执行结果 1:进行中 2:成功 3:失败';
COMMENT ON COLUMN base_data_sync.error_msg IS '异常信息';
COMMENT ON COLUMN base_data_sync.cron_exp IS 'cron表达式';
COMMENT ON COLUMN base_data_sync.next_time IS '下次执行时间';
COMMENT ON COLUMN base_data_sync.sync_type IS '同步类型 1:SQL 2:接口';
COMMENT ON COLUMN base_data_sync.app_key IS '接口模式-开发者账号';
COMMENT ON COLUMN base_data_sync.app_secret IS '接口模式-开发者密码';
COMMENT ON COLUMN base_data_sync.sync_url IS '接口模式-同步URL';
COMMENT ON COLUMN base_data_sync.uid IS '接口模式-数据资产uid';
COMMENT ON COLUMN base_data_sync.after_policy IS '后置处理 bean名.method() 或 全类名.method()';
COMMENT ON COLUMN base_data_sync.before_policy IS '前置处理 bean名.method(参数) 或 全类名.method(参数)';
COMMENT ON COLUMN base_data_sync.body_params IS '接口模式-附加请求体参数JSON';
COMMENT ON COLUMN base_data_sync.creator_time IS '创建时间';
