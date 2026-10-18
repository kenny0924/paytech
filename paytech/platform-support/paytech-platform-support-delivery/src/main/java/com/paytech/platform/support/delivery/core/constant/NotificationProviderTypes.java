package com.paytech.platform.support.delivery.core.constant;

import java.util.Set;

/**
 * 运营可新建的发送账号服务商类型
 * <li>类型是开放目录，按字符串保存；发送时由已注册适配器按类型精确匹配</li>
 */
public final class NotificationProviderTypes {

    /** Amazon SES v2 邮件发送。 */
    public static final String AMAZON_SES = "AMAZON_SES";

    /** 运营可选择的类型。 */
    public static final Set<String> CREATABLE = Set.of(AMAZON_SES);

    /** 常量类不实例化。 */
    private NotificationProviderTypes() {
    }
}
