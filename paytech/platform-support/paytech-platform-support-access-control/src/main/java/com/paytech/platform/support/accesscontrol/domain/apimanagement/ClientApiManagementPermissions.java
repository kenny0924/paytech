package com.paytech.platform.support.accesscontrol.domain.apimanagement;

/** 本能力必须识别的稳定权限编码，权限目录仍由数据库拥有。 */
public final class ClientApiManagementPermissions {
    public static final String CREDENTIAL_READ = "API_CREDENTIAL_READ";
    public static final String CREDENTIAL_REVOKE = "API_CREDENTIAL_REVOKE";
    public static final String SCOPE_READ = "API_SCOPE_READ";
    public static final String SCOPE_MANAGE = "API_SCOPE_MANAGE";
    /** 只承载类型或静态转换，禁止创建无业务意义的实例。 */
    private ClientApiManagementPermissions() {}
}
