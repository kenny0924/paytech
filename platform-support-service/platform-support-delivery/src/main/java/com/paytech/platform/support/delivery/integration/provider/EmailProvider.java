package com.paytech.platform.support.delivery.integration.provider;

/** 邮件供应商边界；真实适配器位于各供应商子包，Mock 仅存在于测试模块。 */
public interface EmailProvider {

    /** 对应不可替换账号身份的 provider_code。 */
    String providerCode();

    /**
     * 是否支持按原请求号查询发送结果
     * <li>支持时 UNKNOWN 只查询原请求、不重发，SENT 继续查询送达</li>
     * <li>不支持时按至少发一次处理：受理即完成，UNKNOWN 等待安全窗口后重发</li>
     */
    default boolean supportsQuery() {
        return true;
    }

    /** 按账号协议构造发送前可持久化的请求号，不调用远端。 */
    String requestNo(long attemptId);

    /** 向唯一收件人发送纯文本邮件；实现不得暗中重试。 */
    EmailProviderResult send(EmailProviderRequest request);

    /**
     * 在登记发送尝试前、事务外准备本次发送所需的账号材料（如从 Vault 读取凭据）。
     * <li>失败时直接抛出异常：此时尚未登记尝试，也未外发，补偿任务稍后重试，不计发送次数</li>
     * <li>默认无需准备，直接使用 send</li>
     *
     * @return 只用于本次发送的发送器
     */
    default EmailSender prepare() {
        return this::send;
    }

    /** 查询原操作，不重发；查询不到默认 UNKNOWN，不能猜测确定失败；仅 supportsQuery 为 true 时调用。 */
    EmailProviderResult query(String requestNo, String messageId);
}
