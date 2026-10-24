package com.ywzt.datasync;

import com.ywzt.datasync.core.DatasyncManager;
import com.ywzt.datasync.repo.SyncTask;
import com.ywzt.datasync.repo.SyncTaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class DatasyncSmokeTest {

    @Autowired
    private DatasyncManager manager;
    @Autowired
    private SyncTaskRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void sqlModeSyncRoundTrip() {
        SyncTask task = new SyncTask();
        task.setCode("smoke-1");
        task.setName("冒烟测试");
        task.setSyncType(1);
        task.setIsEnable("1");
        task.setSourceSql("select id, nm from t_src order by id asc");
        task.setTargetConf("[{\"tableName\":\"t_dst\",\"batchNum\":100,\"sysSyncConfItemVoList\":["
                + "{\"fieldName\":\"id\",\"isCondition\":true,\"isUpdate\":false,\"sourceFieldName\":\"id\"},"
                + "{\"fieldName\":\"nm\",\"isCondition\":false,\"isUpdate\":true,\"sourceFieldName\":\"nm\"}"
                + "]}]");
        manager.save(task);
        assertNotNull(task.getId());

        manager.runNow(task.getId());

        SyncTask reloaded = repository.findById(task.getId());
        assertEquals("2", reloaded.getSyncResult(), "同步应成功, error=" + reloaded.getErrorMsg());
        assertEquals(1L, reloaded.getBatchTotal());

        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, nm FROM t_dst ORDER BY id");
        assertEquals(2, rows.size());
        assertEquals("alpha", rows.get(0).get("NM") == null ? rows.get(0).get("nm") : rows.get(0).get("NM"));

        // 重跑一次验证条件更新而非重复插入
        manager.runNow(task.getId());
        SyncTask rerun = repository.findById(task.getId());
        assertEquals("2", rerun.getSyncResult(), "二次同步应成功, error=" + rerun.getErrorMsg());
        assertEquals(2, jdbc.queryForList("SELECT id FROM t_dst").size());
    }
}
