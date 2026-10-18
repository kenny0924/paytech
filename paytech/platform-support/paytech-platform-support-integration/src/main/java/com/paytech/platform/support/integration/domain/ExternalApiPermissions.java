package com.paytech.platform.support.integration.domain;

/** 外部 API 配置管理的权限编码与资源协调键，取值见注册数据 registration-dml.sql。 */
public final class ExternalApiPermissions {

    /** 查询标准 API、接入方及其下级配置。 */
    public static final String READ = "EXTERNAL_API_READ";

    /** 写入与校验外部 API 配置。 */
    public static final String MANAGE = "EXTERNAL_API_MANAGE";

    /** 全模块共用的写入协调资源（评审点 R4）；模板自动添加 lock: 前缀。 */
    public static final String LOCK_KEY = "external-api-config";

    private ExternalApiPermissions() {
    }
}
