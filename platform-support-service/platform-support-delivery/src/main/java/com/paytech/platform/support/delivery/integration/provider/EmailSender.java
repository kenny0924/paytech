package com.paytech.platform.support.delivery.integration.provider;

/** 已在事务外准备好本次发送材料的单次发送器；只用于一次发送，不缓存、不跨线程传递。 */
@FunctionalInterface
public interface EmailSender {

    /** 向唯一收件人发送纯文本邮件；不得暗中重试。 */
    EmailProviderResult send(EmailProviderRequest request);
}
