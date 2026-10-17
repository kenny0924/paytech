package com.paytech.platform.support.delivery.integration.provider;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationProvider;

/**
 * 邮件服务商边界；按服务商类型注册，账号配置随每次调用传入
 * <li>真实适配器位于各服务商子包，Mock 仅存在于测试模块</li>
 */
public interface EmailProvider {

    /** 适配器声明的服务商类型，对应 t_notification_provider.provider_type。 */
    String providerType();

    /**
     * 是否支持按原请求号查询发送结果
     * <li>支持时 UNKNOWN 只查询原请求、不重发，SENT 继续查询送达</li>
     * <li>不支持时按至少发一次处理：受理即完成，UNKNOWN 等待安全窗口后重发</li>
     */
    default boolean supportsQuery() {
        return true;
    }

    /** 按服务商协议构造发送前可持久化的请求号，不调用远端。 */
    String requestNo(long attemptId);

    /** 以指定账号向唯一收件人发送纯文本邮件；实现不得暗中重试。 */
    EmailProviderResult send(NotificationProvider account, EmailProviderRequest request);

    /**
     * 在登记发送尝试前、事务外准备本次发送所需的账号材料（如从 Vault 读取密钥）。
     * <li>失败时直接抛出异常：此时尚未登记尝试，也未外发，补偿任务稍后重试，不计发送次数</li>
     * <li>默认无需准备，直接使用 send</li>
     *
     * @param account 本次发送使用的账号
     * @return 只用于本次发送的发送器
     */
    default EmailSender prepare(NotificationProvider account) {
        return request -> send(account, request);
    }

    /** 查询原操作，不重发；查询不到默认 UNKNOWN，不能猜测确定失败；仅 supportsQuery 为 true 时调用。 */
    EmailProviderResult query(NotificationProvider account, String requestNo, String messageId);
}
