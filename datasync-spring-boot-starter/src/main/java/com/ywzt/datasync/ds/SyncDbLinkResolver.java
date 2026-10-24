package com.ywzt.datasync.ds;

/**
 * 数据源解析 SPI。
 * 宿主项目注册该 Bean 可用自己的存储（如数据连接表）解析编码，
 * 返回 null 时回落到 yml datasync.datasources 配置。
 */
public interface SyncDbLinkResolver {
    SyncDbLink resolve(String code);
}
