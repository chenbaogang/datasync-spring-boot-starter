package com.ywzt.datasync.ds;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 同步用的数据库连接描述 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncDbLink {
    private String url;
    private String username;
    private String password;
    private String driverClassName;
    /** 可空，为空时按 URL 前缀或数据库元数据识别 */
    private String dbType;
}
