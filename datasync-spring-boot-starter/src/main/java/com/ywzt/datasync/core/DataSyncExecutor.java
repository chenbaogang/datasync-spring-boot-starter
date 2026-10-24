package com.ywzt.datasync.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ywzt.datasync.ds.DbType;
import com.ywzt.datasync.ds.SyncDataSourceManager;
import com.ywzt.datasync.repo.SyncTask;
import com.ywzt.datasync.repo.SyncTaskRepository;
import com.ywzt.datasync.util.DatasyncUtil;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SQL 分页/接口两种来源 -> 字典与日期转换 -> 目标表 存在则更新、不存在则插入。
 */
public class DataSyncExecutor {

    private static final Logger log = LoggerFactory.getLogger(DataSyncExecutor.class);

    private final SyncTaskRepository repository;
    private final SyncDataSourceManager dataSourceManager;
    private final ObjectMapper objectMapper;
    private final ApplicationContext context;
    private final RestTemplate restTemplate = new RestTemplate();
    @Setter
    private Executor triggerExecutor;

    public DataSyncExecutor(SyncTaskRepository repository, SyncDataSourceManager dataSourceManager,
                            ObjectMapper objectMapper, ApplicationContext context) {
        this.repository = repository;
        this.dataSourceManager = dataSourceManager;
        this.objectMapper = objectMapper;
        this.context = context;
    }

    /** 异步触发，提交后立即返回 */
    public void submit(String id, LocalDateTime nextTime) {
        Executor executor = triggerExecutor != null ? triggerExecutor : (Runnable::run);
        executor.execute(() -> {
            try {
                sync(id, nextTime);
            } catch (Exception e) {
                log.error("数据同步任务提交执行失败,任务ID：" + id, e);
            }
        });
    }

    public void sync(String id, LocalDateTime nextTime) {
        SyncTask task = repository.findById(id);
        if (task == null || "1".equals(task.getSyncResult()) || !"1".equals(task.getIsEnable())) {
            return;
        }
        if (!repository.claim(id, nextTime)) {
            log.error("任务ID：{} 正在执行中...", id);
            return;
        }

        List<SyncConf> confList;
        try {
            confList = objectMapper.readValue(task.getTargetConf(), new TypeReference<List<SyncConf>>() {
            });
        } catch (Exception e) {
            repository.updateColumns(id, columns("sync_result", "3", "error_msg", "目标配置解析失败:" + e.getMessage()));
            return;
        }
        task.setSyncResult("1");
        task.setBatchTotal(0L);
        task.setBatchCurrent(0L);
        task.setErrorMsg("");
        task.setNextTime(nextTime);

        // 多表 插入/条件/更新 字段信息
        Map<String, Map<String, List<SyncConfItem>>> changeMap = new LinkedHashMap<>();
        // 字典映射、日期转换规则 key=来源字段
        Map<String, Map<String, List<Map<String, String>>>> columnMapRules = new HashMap<>();
        confList.forEach(conf -> {
            List<SyncConfItem> items = conf.getSysSyncConfItemVoList();
            List<SyncConfItem> insertList = new ArrayList<>();
            List<SyncConfItem> conditionList = new ArrayList<>();
            List<SyncConfItem> updateList = new ArrayList<>();
            for (SyncConfItem item : items) {
                if (item.getDefaultValue() != null || DatasyncUtil.isNotEmpty(item.getSourceFieldName())) {
                    insertList.add(item);
                }
                if (Boolean.TRUE.equals(item.getIsCondition())) {
                    conditionList.add(item);
                }
                if (Boolean.TRUE.equals(item.getIsUpdate())) {
                    updateList.add(item);
                }
                if (DatasyncUtil.isNotEmpty(item.getColumnMapType())
                        && item.getMapDetail() != null && !item.getMapDetail().isEmpty()) {
                    Map<String, List<Map<String, String>>> inner = new HashMap<>();
                    inner.put(item.getColumnMapType(), item.getMapDetail());
                    columnMapRules.put(item.getSourceFieldName(), inner);
                }
            }
            Map<String, List<SyncConfItem>> m = new HashMap<>();
            m.put("insertList", insertList);
            m.put("conditionList", conditionList);
            m.put("updateList", updateList);
            changeMap.put(conf.getTableName(), m);
        });

        boolean sqlMode = task.getSyncType() != null && task.getSyncType() == 1;

        try {
            DataSource sourceDs = dataSourceManager.resolve(task.getSourceCode());
            DataSource targetDs = dataSourceManager.resolve(task.getTargetCode());
            long batchNum = confList.get(0).getBatchNum();
            DbType sourceType = sqlMode ? dataSourceManager.resolveDbType(task.getSourceCode()) : null;
            List<Map<String, Object>> records;
            // 接口调用参数
            Map<String, Object> apiParams = new HashMap<>();
            HttpHeaders headers = new HttpHeaders();
            if (sqlMode) {
                records = readSqlPage(task, sourceDs, sourceType, 1, batchNum);
            } else {
                apiParams.put("current", 1);
                apiParams.put("size", 1000);
                if (DatasyncUtil.isNotEmpty(task.getBodyParams())) {
                    try {
                        apiParams.putAll(objectMapper.readValue(task.getBodyParams(),
                                new TypeReference<Map<String, Object>>() {
                                }));
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                }
                String nowDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                Map<String, String> header = new HashMap<>();
                header.put("Content-Type", "application/json;charset=UTF-8");
                header.put("appKey", task.getAppKey());
                header.put("timeStamp", nowDate);
                header.put("Accept-Charset", "utf-8");
                header.put("authToken", DatasyncUtil.sha1Hex(task.getAppKey() + task.getAppSecret() + nowDate));
                header.put("uid", task.getUid());
                headers.setAll(header);
                records = readApiPage(task, apiParams, headers);
            }

            long batchTotal = task.getBatchTotal() == null ? 0L : task.getBatchTotal();
            for (long i = 1L; i <= batchTotal; i++) {
                task.setBatchCurrent(i);
                if (i != 1L) {
                    if (sqlMode) {
                        records = readSqlPage(task, sourceDs, sourceType, i, batchNum);
                    } else {
                        apiParams.put("current", i);
                        records = readApiPage(task, apiParams, headers);
                    }
                }
                if (records != null && !records.isEmpty()) {
                    // 前置处理
                    if (DatasyncUtil.isNotEmpty(task.getBeforePolicy())) {
                        records = invokeBeforePolicy(task.getBeforePolicy(), records);
                    }
                    // 字典映射、日期转换
                    if (!columnMapRules.isEmpty()) {
                        for (Map<String, Object> record : records) {
                            columnMapRules.forEach((key, rules) -> applyColumnRules(key, rules, record));
                        }
                    }
                    // 逐行写入目标表
                    for (Map<String, Object> data : records) {
                        Map<String, Map<String, Object>> saveOrUpdate = new HashMap<>();
                        AtomicInteger indexFlag = new AtomicInteger();
                        changeMap.forEach((tableName, itemMap) -> {
                            List<SyncConfItem> conditionList = itemMap.get("conditionList");
                            List<SyncConfItem> updateList = itemMap.get("updateList");
                            boolean exists = checkExists(targetDs, tableName, conditionList, data, saveOrUpdate, task);
                            if (exists) {
                                if (!updateList.isEmpty()) {
                                    doUpdate(targetDs, tableName, updateList, conditionList, data, saveOrUpdate, task);
                                }
                            } else {
                                doInsert(targetDs, tableName, itemMap.get("insertList"), data, saveOrUpdate, task);
                            }
                            if (changeMap.size() > 1 && changeMap.size() - 1 != indexFlag.get()) {
                                getCurrentData(targetDs, tableName, conditionList, data, saveOrUpdate, task);
                            }
                            indexFlag.addAndGet(1);
                        });
                    }
                }
                // 更新批次号与最近执行 SQL
                repository.updateColumns(id, columns("batch_current", task.getBatchCurrent(),
                        "batch_total", task.getBatchTotal(), "exe_sql", task.getExeSql()));
            }
            // 更新成功
            repository.updateColumns(id, columns("sync_result", "2", "error_msg", ""));

            // 后置处理
            if (DatasyncUtil.isNotEmpty(task.getAfterPolicy())) {
                Boolean flag = invokeAfterPolicy(task.getAfterPolicy());
                if (flag == null || !flag) {
                    throw new RuntimeException("后置处理失败");
                }
            }
        } catch (Exception e) {
            log.error("数据同步任务失败,任务ID：" + id, e);
            String msg = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
            repository.updateColumns(id, columns("sync_result", "3",
                    "error_msg", msg.length() > 2000 ? msg.substring(0, 2000) : msg));
        }
    }

    // ---------------- 来源读取 ----------------

    /** SQL 模式：按来源库方言分页，返回小写列名 key */
    private List<Map<String, Object>> readSqlPage(SyncTask task, DataSource ds, DbType dbType,
                                                  long current, long size) {
        String baseSql = DatasyncUtil.extractBeforeLastOrderBy(task.getSourceSql());
        String sortType;
        try {
            String afterOrderBy = DatasyncUtil.extractAfterLastOrderBy(task.getSourceSql());
            String[] split = afterOrderBy.trim().split(",")[0].trim().split("\\s+");
            sortType = split.length > 1 ? split[0] + " " + split[1] : split[0];
            if (!sortType.matches("[A-Za-z0-9_$.]+( (?i:ASC|DESC))?")) {
                throw new IllegalArgumentException("非法排序列: " + sortType);
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        if (task.getBatchTotal() == null || task.getBatchTotal() == 0L) {
            Long total = jdbc.queryForObject("SELECT COUNT(1) FROM (" + baseSql + ") ds_total_t", Long.class);
            if (total == null) {
                total = 0L;
            }
            task.setBatchTotal((total + size - 1) / size);
        }
        String pageSql = dbType.pageSql(baseSql, sortType, (current - 1) * size, size);
        List<Map<String, Object>> rows = jdbc.queryForList(pageSql);
        List<Map<String, Object>> records = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> lower = new LinkedHashMap<>();
            row.forEach((k, v) -> lower.put(k.toLowerCase(), v instanceof Timestamp ? new Date(((Timestamp) v).getTime()) : v));
            records.add(lower);
        }
        return records;
    }

    /** 接口模式：分页协议 {data:{pages,records}} */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Map<String, Object>> readApiPage(SyncTask task, Map<String, Object> params, HttpHeaders headers) {
        HttpEntity<Map> entity = new HttpEntity<>(params, headers);
        ResponseEntity<Map> exchange = restTemplate.postForEntity(task.getSyncUrl(), entity, Map.class);
        Map body = exchange.getBody();
        Map dataResult = (Map) body.get("data");
        Number pages = (Number) dataResult.get("pages");
        if (task.getBatchTotal() == null || task.getBatchTotal() == 0L) {
            task.setBatchTotal(pages == null ? 0L : pages.longValue());
        }
        return (List<Map<String, Object>>) dataResult.get("records");
    }

    // ---------------- 转换规则 ----------------

    private void applyColumnRules(String key, Map<String, List<Map<String, String>>> rules, Map<String, Object> record) {
        // 字典映射
        List<Map<String, String>> dictRules = rules.get("1");
        if (dictRules != null) {
            try {
                String updKey = null;
                for (Map<String, String> map : dictRules) {
                    Object cur = record.get(key) == null ? "" : record.get(key);
                    if (String.valueOf(map.get("sourceValue")).equals(cur.toString())) {
                        if (updKey == null || !key.equals(updKey)) {
                            record.put(key, map.get("targetValue"));
                            updKey = key;
                        }
                    }
                }
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
        // 日期格式转换
        for (String type : new String[]{"2", "3"}) {
            List<Map<String, String>> dateRules = rules.get(type);
            if (dateRules == null) {
                continue;
            }
            for (Map<String, String> map : dateRules) {
                try {
                    String originType = map.get("sourceValue");
                    String targetType = map.get("targetValue");
                    Object raw = record.get(key);
                    if (raw == null || DatasyncUtil.isEmpty(String.valueOf(raw))) {
                        continue;
                    }
                    Date parse = new SimpleDateFormat(originType).parse(String.valueOf(raw));
                    record.put(key, new SimpleDateFormat(targetType).format(parse));
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
    }

    // ---------------- 目标表写入 ----------------

    private Object resolveValue(SyncConfItem item, Map<String, Object> data,
                                Map<String, Map<String, Object>> saveOrUpdate) {
        String source = item.getSourceFieldName();
        if (DatasyncUtil.isRule(source)) {
            String[] split = source.substring(2, source.lastIndexOf("}")).split("\\.");
            Map<String, Object> row = saveOrUpdate.get(split[0]);
            return row == null ? null : row.get(split[1]);
        }
        return DatasyncUtil.isEmpty(source) ? item.getDefaultValue() : data.get(source);
    }

    private static void appendWhere(StringBuilder sql, List<Object> args, String column, Object value) {
        if (sql.length() > 0) {
            sql.append(" AND ");
        }
        quoteIdent(sql, column);
        sql.append(" = ?");
        args.add(value);
    }

    private static void quoteIdent(StringBuilder sql, String ident) {
        // 兼容 MySQL/PG（PG 未加引号标识符折叠为小写）
        // 仅做字符合法性校验防注入
//        String regex = "[A-Za-z0-9_$.]+";
        String regex = "[A-Za-z0-9_$.\\u4e00-\\u9fa5]+";
        if (ident == null || !ident.matches(regex)) {
            throw new RuntimeException("非法标识符: " + ident);
        }
        sql.append(ident);
    }

    private boolean checkExists(DataSource ds, String tableName, List<SyncConfItem> conditionList,
                                Map<String, Object> data, Map<String, Map<String, Object>> saveOrUpdate,
                                SyncTask task) {
        StringBuilder sql = new StringBuilder("SELECT ");
        quoteIdent(sql, conditionList.get(0).getFieldName());
        sql.append(" FROM ");
        quoteIdent(sql, tableName);
        sql.append(" WHERE ");
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder();
        conditionList.forEach(item -> appendWhere(where, args, item.getFieldName(), resolveValue(item, data, saveOrUpdate)));
        sql.append(where);
        task.setExeSql(sql.toString());
        List<Map<String, Object>> list = new JdbcTemplate(ds).queryForList(sql.toString(), args.toArray());
        return !list.isEmpty();
    }

    private void doUpdate(DataSource ds, String tableName, List<SyncConfItem> updateList,
                          List<SyncConfItem> conditionList, Map<String, Object> data,
                          Map<String, Map<String, Object>> saveOrUpdate, SyncTask task) {
        StringBuilder sql = new StringBuilder("UPDATE ");
        quoteIdent(sql, tableName);
        List<Object> args = new ArrayList<>();
        StringBuilder set = new StringBuilder();
        for (SyncConfItem item : updateList) {
            if (set.length() > 0) {
                set.append(", ");
            }
            quoteIdent(set, item.getFieldName());
            set.append(" = ?");
            args.add(resolveValue(item, data, saveOrUpdate));
        }
        sql.append(" SET ").append(set);
        sql.append(" WHERE ");
        StringBuilder where = new StringBuilder();
        conditionList.forEach(item -> appendWhere(where, args, item.getFieldName(), resolveValue(item, data, saveOrUpdate)));
        sql.append(where);
        task.setExeSql(sql.toString());
        new JdbcTemplate(ds).update(sql.toString(), args.toArray());
    }

    private void doInsert(DataSource ds, String tableName, List<SyncConfItem> insertList,
                          Map<String, Object> data, Map<String, Map<String, Object>> saveOrUpdate, SyncTask task) {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        quoteIdent(sql, tableName);
        StringBuilder cols = new StringBuilder();
        StringBuilder holder = new StringBuilder();
        List<Object> args = new ArrayList<>();
        for (SyncConfItem item : insertList) {
            if (cols.length() > 0) {
                cols.append(", ");
                holder.append(", ");
            }
            quoteIdent(cols, item.getFieldName());
            holder.append('?');
            args.add(resolveValue(item, data, saveOrUpdate));
        }
        sql.append(" (").append(cols).append(") VALUES (").append(holder).append(')');
        String insertSql = sql.toString();
        task.setExeSql(insertSql);
        new JdbcTemplate(ds).update(insertSql, args.toArray());
    }

    private void getCurrentData(DataSource ds, String tableName, List<SyncConfItem> conditionList,
                                Map<String, Object> data, Map<String, Map<String, Object>> saveOrUpdate,
                                SyncTask task) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ");
        quoteIdent(sql, tableName);
        sql.append(" WHERE ");
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder();
        conditionList.forEach(item -> appendWhere(where, args, item.getFieldName(), resolveValue(item, data, saveOrUpdate)));
        sql.append(where);
        task.setExeSql(sql.toString());
        List<Map<String, Object>> list = new JdbcTemplate(ds).queryForList(sql.toString(), args.toArray());
        if (!list.isEmpty()) {
            saveOrUpdate.put(tableName, list.get(0));
        }
    }

    // ---------------- 前/后置策略 ----------------

    private List<Map<String, Object>> invokeBeforePolicy(String policy, List<Map<String, Object>> records) {
        Object bean = resolvePolicyBean(policy);
        String methodName = DatasyncUtil.substringAfterLast(
                DatasyncUtil.substringBefore(policy, "("), ".");
        try {
            List<Object[]> methodParams = new LinkedList<>();
            methodParams.add(new Object[]{records, List.class});
            String params = DatasyncUtil.substringBeforeLast(
                    DatasyncUtil.substringAfterLast(policy, "("), ")");
            if (DatasyncUtil.isNotEmpty(params)) {
                for (String s : params.split(",")) {
                    methodParams.add(new Object[]{s, String.class});
                }
            }
            Class<?>[] types = new Class<?>[methodParams.size()];
            Object[] values = new Object[methodParams.size()];
            for (int i = 0; i < methodParams.size(); i++) {
                types[i] = (Class<?>) methodParams.get(i)[1];
                values[i] = methodParams.get(i)[0];
            }
            Method method = bean.getClass().getDeclaredMethod(methodName, types);
            return castRecords(method.invoke(bean, values));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private Boolean invokeAfterPolicy(String policy) {
        Object bean = resolvePolicyBean(policy);
        String methodName = DatasyncUtil.substringAfterLast(
                DatasyncUtil.substringBefore(policy, "("), ".");
        try {
            Method method = bean.getClass().getDeclaredMethod(methodName);
            Object result = method.invoke(bean);
            return result instanceof Boolean ? (Boolean) result : null;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /** beanName 含多个点视为全类名反射实例化，否则按 Spring bean 名取 */
    private Object resolvePolicyBean(String policy) {
        String beanName = DatasyncUtil.substringBeforeLast(
                DatasyncUtil.substringBefore(policy, "("), ".");
        try {
            if (beanName.chars().filter(c -> c == '.').count() > 1) {
                return Class.forName(beanName).newInstance();
            }
            return context.getBean(beanName);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("前/后置处理类实例化失败: " + beanName, e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castRecords(Object result) {
        return (List<Map<String, Object>>) result;
    }

    private static Map<String, Object> columns(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }
}
