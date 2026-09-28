package com.paytech.platform.support.delivery.integration.provider;

/** 邮件供应商边界；真实供应商尚未选型，Mock 仅存在于测试模块。 */
public interface EmailProvider {

    /** 对应不可替换账号身份的 provider_code。 */
    String providerCode();

    /** 按账号协议构造发送前可持久化的请求号，不调用远端。 */
    String requestNo(long attemptId);

    /** 向唯一收件人发送纯文本邮件；实现不得暗中重试。 */
    EmailProviderResult send(EmailProviderRequest request);

    /** 查询原操作，不重发；查询不到默认 UNKNOWN，不能猜测确定失败。 */
    EmailProviderResult query(String requestNo, String messageId);
}
