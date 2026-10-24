package com.ywzt.datasync.core;

import com.ywzt.datasync.repo.SyncTask;
import com.ywzt.datasync.repo.SyncTaskRepository;

import java.util.List;

/** 宿主项目可直接注入该 Bean 以编程方式管理同步任务 */
public class DatasyncManager {

    private final SyncTaskRepository repository;
    private final DataSyncExecutor executor;

    public DatasyncManager(SyncTaskRepository repository, DataSyncExecutor executor) {
        this.repository = repository;
        this.executor = executor;
    }

    public List<SyncTask> page(String name, long currentPage, long pageSize) {
        return repository.findPage(name, currentPage, pageSize);
    }

    public long count(String name) {
        return repository.count(name);
    }

    public SyncTask getById(String id) {
        return repository.findById(id);
    }

    public SyncTask save(SyncTask task) {
        if (task.getSyncType() != null && task.getSyncType() == 1
                && (task.getSourceSql() == null || !task.getSourceSql().matches("(?si).*\\border\\s+by\\b.*"))) {
            throw new IllegalArgumentException("来源SQL必须包含[order by 字段]");
        }
        if (repository.existsCode(task.getCode())) {
            throw new IllegalArgumentException("任务编码不能重复!");
        }
        repository.insert(task);
        return task;
    }

    public void updateById(SyncTask task) {
        if (task.getSyncType() != null && task.getSyncType() == 1
                && task.getSourceSql() != null && !task.getSourceSql().matches("(?si).*\\border\\s+by\\b.*")) {
            throw new IllegalArgumentException("来源SQL必须包含[order by 字段]");
        }
        repository.updateNonNull(task);
    }

    public void deleteById(String id) {
        repository.deleteById(id);
    }

    /** 异步触发一次同步 */
    public void trigger(String id) {
        executor.submit(id, null);
    }

    /** 同步执行一次（阻塞直到完成） */
    public void runNow(String id) {
        executor.sync(id, null);
    }
}
