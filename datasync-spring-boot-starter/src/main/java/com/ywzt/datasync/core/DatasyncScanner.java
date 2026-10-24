package com.ywzt.datasync.core;

import com.ywzt.datasync.repo.SyncTask;
import com.ywzt.datasync.repo.SyncTaskRepository;
import com.ywzt.datasync.util.DatasyncUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 每个任务按自身 cronExp 计算的 nextTime 到期后异步提交执行。
 */
public class DatasyncScanner {

    private static final Logger log = LoggerFactory.getLogger(DatasyncScanner.class);

    private final SyncTaskRepository repository;
    private final DataSyncExecutor executor;
    private final long intervalMs;
    private ScheduledExecutorService scheduler;

    public DatasyncScanner(SyncTaskRepository repository, DataSyncExecutor executor, long intervalMs) {
        this.repository = repository;
        this.executor = executor;
        this.intervalMs = intervalMs;
    }

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "datasync-scanner");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::scanOnce, 5000, intervalMs, TimeUnit.MILLISECONDS);
        log.info("数据同步扫描器已启动，间隔{}ms", intervalMs);
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public void scanOnce() {
        log.debug("开始执行数据同步任务列表扫描时间：{}", LocalDateTime.now());
        LocalDateTime now = LocalDateTime.now();
        List<SyncTask> list = repository.findEnabled();
        for (SyncTask item : list) {
            if (DatasyncUtil.isEmpty(item.getCronExp()) || "1".equals(item.getSyncResult())) {
                continue;
            }
            try {
                LocalDateTime exeTime = item.getNextTime();
                if (exeTime == null || !now.isBefore(exeTime)) {
                    LocalDateTime nextTime = computeNextTime(item.getCronExp(), exeTime == null ? now : exeTime, now);
                    executor.submit(item.getId(), nextTime);
                }
            } catch (Exception e) {
                log.error("数据同步任务调度失败,任务ID：" + item.getId(), e);
            }
        }
        log.debug("结束执行数据同步任务列表扫描时间：{}", LocalDateTime.now());
    }

    /** 取下一个严格晚于 now 的执行时间，等价原 getNextTime 递归 */
    private LocalDateTime computeNextTime(String cronExp, LocalDateTime from, LocalDateTime now) {
        CronExpression cron = CronExpression.parse(cronExp);
        LocalDateTime cursor = from;
        for (int guard = 0; guard < 1000; guard++) {
            LocalDateTime next = cron.next(cursor);
            if (next == null) {
                return null;
            }
            if (next.isAfter(now)) {
                return next;
            }
            cursor = next;
        }
        return cursor;
    }
}
