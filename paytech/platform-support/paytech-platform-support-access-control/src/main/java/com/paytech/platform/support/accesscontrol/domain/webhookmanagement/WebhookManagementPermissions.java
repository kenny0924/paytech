package com.paytech.platform.support.accesscontrol.domain.webhookmanagement;

/** Operations Webhook 管理的权限编码，与初始化 SQL 的 t_permission 一致。 */
public final class WebhookManagementPermissions {

    /** 查看端点与投递记录。 */
    public static final String READ = "WEBHOOK_READ";

    /** 维护端点、轮换密钥、发送测试事件与 Replay。 */
    public static final String MANAGE = "WEBHOOK_MANAGE";

    /** 只承载常量。 */
    private WebhookManagementPermissions() {}
}
