CREATE TABLE IF NOT EXISTS base_data_sync (
  id varchar(50) PRIMARY KEY,
  code varchar(255),
  name varchar(255),
  source_code varchar(255),
  target_code varchar(255),
  source_sql text,
  target_conf longtext,
  exe_sql longtext,
  batch_total bigint,
  batch_current bigint,
  is_enable varchar(2),
  sync_result varchar(2),
  error_msg text,
  cron_exp varchar(255),
  next_time datetime,
  sync_type int,
  app_key varchar(255),
  app_secret varchar(255),
  sync_url varchar(500),
  uid varchar(255),
  after_policy varchar(500),
  before_policy varchar(500),
  body_params text,
  creator_time datetime
);
CREATE TABLE IF NOT EXISTS t_src (id varchar(50), nm varchar(255));
CREATE TABLE IF NOT EXISTS t_dst (id varchar(50), nm varchar(255));
DELETE FROM base_data_sync WHERE code = 'smoke-1';
DELETE FROM t_src;
INSERT INTO t_src VALUES ('1', 'alpha'), ('2', 'beta');
DELETE FROM t_dst;
