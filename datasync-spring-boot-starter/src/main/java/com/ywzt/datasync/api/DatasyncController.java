package com.ywzt.datasync.api;

import com.ywzt.datasync.core.DatasyncManager;
import com.ywzt.datasync.core.DatasyncScanner;
import com.ywzt.datasync.repo.SyncTask;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("${datasync.api.path:/datasync}")
public class DatasyncController {

    private final DatasyncManager manager;
    private final ObjectProvider<DatasyncScanner> scannerProvider;

    public DatasyncController(DatasyncManager manager, ObjectProvider<DatasyncScanner> scannerProvider) {
        this.manager = manager;
        this.scannerProvider = scannerProvider;
    }

    @GetMapping("/page")
    public ApiResult<Map<String, Object>> page(@RequestParam(required = false) String name,
                                               @RequestParam(defaultValue = "1") long currentPage,
                                               @RequestParam(defaultValue = "20") long pageSize) {
        List<SyncTask> list = manager.page(name, currentPage, pageSize);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", manager.count(name));
        return ApiResult.success(data);
    }

    @GetMapping("/{id}")
    public ApiResult<SyncTask> getById(@PathVariable String id) {
        return ApiResult.success(manager.getById(id));
    }

    @PostMapping("/save")
    public ApiResult<SyncTask> save(@RequestBody SyncTask task) {
        try {
            return ApiResult.build(200, "保存成功", manager.save(task));
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(e.getMessage());
        }
    }

    @PostMapping("/updateById")
    public ApiResult<String> updateById(@RequestBody SyncTask task) {
        try {
            manager.updateById(task);
            return ApiResult.build(200, "更新成功", null);
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(e.getMessage());
        }
    }

    @GetMapping("/deleteById/{id}")
    public ApiResult<String> deleteById(@PathVariable String id) {
        manager.deleteById(id);
        return ApiResult.build(200, "删除成功", null);
    }

    /** 手动触发一次同步（异步执行） */
    @GetMapping("/dataSync/{id}")
    public ApiResult<String> dataSync(@PathVariable String id) {
        manager.trigger(id);
        return ApiResult.success("数据同步中，请自行查看同步结果!");
    }

    /** 手动执行一轮任务扫描 */
    @GetMapping("/scan")
    public ApiResult<String> scan() {
        DatasyncScanner scanner = scannerProvider.getIfAvailable();
        if (scanner == null) {
            return ApiResult.fail("内置扫描器未启用(datasync.scheduler.enabled=false)");
        }
        scanner.scanOnce();
        return ApiResult.success("扫描完成");
    }
}
