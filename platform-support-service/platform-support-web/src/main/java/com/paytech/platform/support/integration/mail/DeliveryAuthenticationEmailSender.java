package com.paytech.platform.support.integration.mail;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientEmailChallengePolicy.LinkMaterial;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengePurpose;
import com.paytech.platform.support.accesscontrol.integration.mail.AuthenticationEmailSender;
import com.paytech.platform.support.delivery.domain.NotificationInput;
import com.paytech.platform.support.delivery.integration.provider.EmailProviderResult.Outcome;
import com.paytech.platform.support.delivery.service.processor.SendTransientEmailProcessor;
import com.paytech.platform.support.delivery.service.processor.request.SendTransientEmailRequest;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 认证邮件经 Delivery 即时发送：模板、发送账号与供应商和普通通知共用，链接只在本次内存中使用。
 * <li>只发送固定站点链接，秘密放在 Fragment 中，页面 GET 和 Referer 不携带秘密</li>
 * <li>任何失败都计指标并转为 ServiceUnavailableException，原异常、收件人和链接不进入日志</li>
 */
public final class DeliveryAuthenticationEmailSender implements AuthenticationEmailSender {

    /** 注册验证邮件模板。 */
    public static final String VERIFICATION_TEMPLATE = "CLIENT_EMAIL_VERIFICATION";

    /** 密码重置邮件模板。 */
    public static final String PASSWORD_RESET_TEMPLATE = "CLIENT_PASSWORD_RESET";

    /** 注册请求暂无语言字段，首期固定中文。 */
    public static final String LOCALE = "zh-CN";

    /** 投递结果指标。 */
    static final String METRIC = "client.authentication.email.delivery";

    private final SendTransientEmailProcessor processor;
    private final String publicOrigin;
    private final MeterRegistry metrics;

    /** 固定部署域必须是 HTTPS 或显式本机开发域，不接受任意 URL。 */
    public DeliveryAuthenticationEmailSender(SendTransientEmailProcessor processor, String publicOrigin,
        MeterRegistry metrics) {
        URI origin = URI.create(publicOrigin);
        boolean local = origin.getHost() != null && "http".equals(origin.getScheme())
            && Set.of("localhost", "127.0.0.1", "[::1]").contains(origin.getHost());
        if (origin.getHost() == null || !("https".equals(origin.getScheme()) || local)
            || origin.getRawUserInfo() != null || origin.getRawQuery() != null || origin.getRawFragment() != null
            || !(origin.getPath().isEmpty() || "/".equals(origin.getPath()))) {
            throw new IllegalArgumentException("Client 邮件链接必须绑定固定站点 Origin");
        }
        this.processor = processor;
        this.publicOrigin = publicOrigin.replaceAll("/$", "");
        this.metrics = metrics;
    }

    /** 按用途选择模板与页面路径，只发送一次。 */
    @Override
    public void send(String canonicalEmail, ClientChallengePurpose purpose, LinkMaterial link) {
        boolean signUp = purpose == ClientChallengePurpose.SIGN_UP_EMAIL;
        var request = new SendTransientEmailRequest();
        request.setTemplateCode(signUp ? VERIFICATION_TEMPLATE : PASSWORD_RESET_TEMPLATE);
        request.setEmail(canonicalEmail);
        request.setLocale(LOCALE);
        request.setParameters(Map.of(NotificationInput.TRANSIENT_LINK_PARAMETER, publicOrigin + (signUp ? "/verify-email" : "/reset-password")
            + "#challengeNo=" + link.challengeNo() + "&secret=" + link.secret()));
        String failure;
        try {
            failure = failureReason(processor.process(request).getOutcome());
        } catch (FailException invalidConfiguration) {
            failure = "CONFIGURATION";
        } catch (RuntimeException unavailable) {
            // Vault、适配器或其他依赖异常可能携带上下文，不交给普通日志处理器。
            failure = "DEPENDENCY";
        }
        if (failure == null) {
            metrics.counter(METRIC, "result", "accepted").increment();
            return;
        }
        metrics.counter(METRIC, "result", "failed", "reason", failure).increment();
        throw new ServiceUnavailableException();
    }

    /** 供应商受理为成功；其余结果给出有界原因。 */
    static String failureReason(Outcome outcome) {
        return switch (outcome) {
            case ACCEPTED, DELIVERED -> null;
            case UNKNOWN -> "UNKNOWN";
            case REJECTED, RETRYABLE_REJECTED, UNDELIVERABLE -> "REJECTED";
        };
    }
}
