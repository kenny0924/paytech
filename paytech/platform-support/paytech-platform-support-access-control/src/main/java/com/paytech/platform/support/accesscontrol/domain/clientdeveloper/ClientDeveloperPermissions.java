package com.paytech.platform.support.accesscontrol.domain.clientdeveloper;

import java.util.List;

/** CLIENT 工作区开发者能力的权限码；各系统角色的授予矩阵见 ClientTeamPermissions。 */
public final class ClientDeveloperPermissions {

    /** 查看本工作区 API 凭证。 */
    public static final String API_CREDENTIAL_READ = "CLIENT_API_CREDENTIAL_READ";

    /** 创建、轮换、撤销本工作区 API 凭证。 */
    public static final String API_CREDENTIAL_MANAGE = "CLIENT_API_CREDENTIAL_MANAGE";

    /** 查看本工作区 Webhook 端点与投递。 */
    public static final String WEBHOOK_READ = "CLIENT_WEBHOOK_READ";

    /** 维护本工作区 Webhook 端点、密钥、测试事件与重发。 */
    public static final String WEBHOOK_MANAGE = "CLIENT_WEBHOOK_MANAGE";

    /** 全部开发者权限（所有者与管理员拥有）。 */
    public static final List<String> OWNER_DEFAULTS = List.of(API_CREDENTIAL_READ, API_CREDENTIAL_MANAGE, WEBHOOK_READ,
        WEBHOOK_MANAGE);

    private ClientDeveloperPermissions() {}
}
