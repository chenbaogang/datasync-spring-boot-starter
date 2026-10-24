package com.ywzt.datasync.repo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

/** 数据同步任务 */
@Data
public class SyncTask {
    private String id;
    /** 任务编号 */
    private String code;
    /** 任务名称 */
    private String name;
    /** 来源数据源编码 */
    private String sourceCode;
    /** 目标数据源编码 */
    private String targetCode;
    /** 来源sql */
    private String sourceSql;
    /** 目标配置(JSON) */
    private String targetConf;
    /** 最近一次执行sql */
    private String exeSql;
    /** 总批次 */
    private Long batchTotal;
    /** 当前批次 */
    private Long batchCurrent;
    /** 是否开启同步（1：开启 0：关闭） */
    private String isEnable;
    /** 同步执行结果（1：进行中 2：同步成功 3：同步失败） */
    private String syncResult;
    /** 异常信息 */
    private String errorMsg;
    /** cron表达式 */
    private String cronExp;
    /** 下次执行时间 */
    private LocalDateTime nextTime;
    /** 同步类型 1:SQL 2:接口 */
    private Integer syncType;
    private String appKey;
    private String appSecret;
    private String syncUrl;
    private String uid;
    /** 后置处理方法 */
    private String afterPolicy;
    /** 前处理方法 */
    private String beforePolicy;
    /** 接口调用参数(JSON) */
    private String bodyParams;
    private Date creatorTime;
}
