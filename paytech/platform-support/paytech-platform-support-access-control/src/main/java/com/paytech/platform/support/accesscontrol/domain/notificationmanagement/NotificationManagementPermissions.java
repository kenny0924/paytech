package com.paytech.platform.support.accesscontrol.domain.notificationmanagement;

/** Operations 通知管理的权限编码，与初始化 SQL 的 t_permission 一致。 */
public final class NotificationManagementPermissions {

    /** 查看模板、发送账号与发送记录。 */
    public static final String READ = "NOTIFICATION_READ";

    /** 维护模板、语言内容、路由，新建、编辑与启停账号，预览内容。 */
    public static final String MANAGE = "NOTIFICATION_MANAGE";

    /** 录入、更换发送账号密钥与测试发送。 */
    public static final String CREDENTIAL_MANAGE = "NOTIFICATION_CREDENTIAL_MANAGE";

    /** 只承载常量。 */
    private NotificationManagementPermissions() {}
}
