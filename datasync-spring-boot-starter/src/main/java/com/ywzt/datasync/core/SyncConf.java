package com.ywzt.datasync.core;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 目标表同步配置，字段名与 base_data_sync.target_conf 的 JSON 保持一致 */
@Data
public class SyncConf implements Serializable {
    private String tableName;
    private Long batchNum = 1000L;
    private List<SyncConfItem> sysSyncConfItemVoList;
}
