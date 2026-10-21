package com.paytech.channel.connector.protocol.domain;

/** 外部 API 配置写入的资源协调键：全部配置写入口与沙箱运行发起共用同一资源。 */
public final class ExternalApiLocks {

    /** 全模块共用的写入协调资源（评审点 R4）；模板自动添加 lock: 前缀，使用 channel-connector 的 Redis 前缀。 */
    public static final String LOCK_KEY = "external-api-config";

    /** 常量类不实例化。 */
    private ExternalApiLocks() {
    }
}
