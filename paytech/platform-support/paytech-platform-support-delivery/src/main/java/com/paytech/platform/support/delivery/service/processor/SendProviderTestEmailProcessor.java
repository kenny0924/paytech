package com.paytech.platform.support.delivery.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationProvider;
import com.paytech.platform.support.delivery.domain.NotificationInput;
import com.paytech.platform.support.delivery.domainservice.repository.NotificationProviderRepository;
import com.paytech.platform.support.delivery.integration.provider.EmailProviderRequest;
import com.paytech.platform.support.delivery.integration.provider.EmailProviderResult;
import com.paytech.platform.support.delivery.integration.provider.EmailProviders;
import com.paytech.platform.support.delivery.service.processor.request.SendProviderTestEmailRequest;
import com.paytech.platform.support.delivery.service.processor.response.SendProviderTestEmailResponse;

import lombok.RequiredArgsConstructor;

/**
 * 模块公开入口：用指定账号向指定邮箱发送一封固定内容的测试邮件
 * <li>不检查账号启停（允许启用前验证），但要求配置完整；没有事务，不写发送记录、不重试</li>
 * <li>调用方须在数据库事务外调用并已完成鉴权；Vault 与服务商调用都在事务外</li>
 * <li>本环境未开启通知发送或未注册该类型适配器时返回 503</li>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class SendProviderTestEmailProcessor
    extends AbstractProcessor<SendProviderTestEmailRequest, SendProviderTestEmailResponse> {

    /** 测试邮件标题。 */
    static final String SUBJECT = "PAYTECH 发送账号测试";

    private final Clock clock;
    private final IdGenerator ids;
    private final ObjectProvider<EmailProviders> adapters;
    private final NotificationProviderRepository providers;

    @Override
    protected SendProviderTestEmailResponse processRequest(SendProviderTestEmailRequest request) {
        String recipient = NotificationInput.canonicalEmail(request.getEmail());
        NotificationProvider account = requireSendableAccount(request.getProviderCode());
        EmailProviders registry = adapters.getIfAvailable();
        if (registry == null) {
            throw new ServiceUnavailableException();
        }
        var provider = registry.require(account);
        var sender = provider.prepare(account);
        var result = sender.send(new EmailProviderRequest(provider.requestNo(ids.nextId()), recipient, SUBJECT,
            body(account.getProviderCode(), clock.instant())));
        return response(recipient, result);
    }

    /** 账号存在且配置完整；不检查启停。 */
    NotificationProvider requireSendableAccount(String providerCode) {
        NotificationProvider account = providers.findCode(providerCode);
        if (account == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        var missing = account.missingConfiguration();
        if (!missing.isEmpty()) {
            throw new BizCheckFailException("账号配置不完整，缺少: " + String.join("、", missing));
        }
        return account;
    }

    /** 固定正文：账号编码与发送时间，无链接。 */
    static String body(String providerCode, Instant now) {
        return "这是一封发送账号测试邮件，无需回复。\n\n账号：" + providerCode + "\n发送时间（UTC）："
            + now.truncatedTo(ChronoUnit.SECONDS);
    }

    /** 只返回有界结果码；缺分类或结果码不合法时按未知处理。 */
    static SendProviderTestEmailResponse response(String recipient, EmailProviderResult result) {
        if (result.getOutcome() == null || result.getCode() == null
            || !result.getCode().matches("[A-Z][A-Z0-9_]{0,63}")) {
            return new SendProviderTestEmailResponse(recipient, EmailProviderResult.Outcome.UNKNOWN, "RESULT_UNKNOWN");
        }
        return new SendProviderTestEmailResponse(recipient, result.getOutcome(), result.getCode());
    }
}
