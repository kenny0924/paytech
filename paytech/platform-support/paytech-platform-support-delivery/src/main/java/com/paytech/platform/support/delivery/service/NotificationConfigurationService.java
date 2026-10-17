package com.paytech.platform.support.delivery.service;

import com.paytech.platform.support.delivery.domain.NotificationRouting;

/** 发送前的当前配置检查；只读本库，不调用 Vault 或 Provider。 */
public interface NotificationConfigurationService {

    /**
     * 解析模板当前路由
     * <li>模板须为 EMAIL 且 ACTIVE，路由 ACTIVE，主备账号均为 EMAIL、ACTIVE 且恰有一个适配器</li>
     *
     * @param templateCode 模板编码
     * @return 已校验的模板、路由与主账号
     */
    NotificationRouting resolve(String templateCode);
}
