package com.paytech.platform.support.integration.mail;

import java.net.URI;
import java.util.Set;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientEmailChallengePolicy.LinkMaterial;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengePurpose;
import com.paytech.platform.support.accesscontrol.integration.mail.AuthenticationEmailSender;

import io.micrometer.core.instrument.MeterRegistry;

/** SMTP 适配器只发送固定域链接；正文、收件人和可消费秘密不进入日志或重试队列。 */
public final class SmtpAuthenticationEmailSender implements AuthenticationEmailSender {
    private final JavaMailSender sender;
    private final String from;
    private final String publicOrigin;
    private final MeterRegistry metrics;

    /** 固定部署域必须是 HTTPS 或显式本机开发域，不接受任意 URL。 */
    public SmtpAuthenticationEmailSender(JavaMailSender sender, String from, String publicOrigin, MeterRegistry metrics) {
        URI origin = URI.create(publicOrigin);
        boolean local = origin.getHost() != null && "http".equals(origin.getScheme())
            && Set.of("localhost", "127.0.0.1", "[::1]").contains(origin.getHost());
        if (origin.getHost() == null || !("https".equals(origin.getScheme()) || local)
            || origin.getRawUserInfo() != null || origin.getRawQuery() != null || origin.getRawFragment() != null
            || !(origin.getPath().isEmpty() || "/".equals(origin.getPath()))) {
            throw new IllegalArgumentException("Client 邮件链接必须绑定固定站点 Origin");
        }
        if (from == null || from.isBlank() || from.contains("\r") || from.contains("\n")) {
            throw new IllegalArgumentException("邮件发件人配置无效");
        }
        this.sender = sender;
        this.from = from;
        this.publicOrigin = publicOrigin.replaceAll("/$", "");
        this.metrics = metrics;
    }

    /** 链接秘密放在 Fragment 中，页面 GET 和 Referer 不携带秘密。 */
    @Override
    public void send(String canonicalEmail, ClientChallengePurpose purpose, LinkMaterial link) {
        String path = purpose == ClientChallengePurpose.SIGN_UP_EMAIL ? "/verify-email" : "/reset-password";
        String title = purpose == ClientChallengePurpose.SIGN_UP_EMAIL ? "验证您的邮箱" : "重置登录密码";
        String url = publicOrigin + path + "#challengeNo=" + link.challengeNo() + "&secret=" + link.secret();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(canonicalEmail);
        message.setSubject("PAYTECH · " + title);
        message.setText(title + "\n\n请打开以下链接，并在页面中确认操作：\n" + url
            + "\n\n如果不是您发起的请求，请忽略此邮件。");
        try {
            sender.send(message);
            metrics.counter("client.authentication.email.delivery", "result", "accepted").increment();
        } catch (MailException ignored) {
            metrics.counter("client.authentication.email.delivery", "result", "failed").increment();
            // MailException 可能包含地址和正文，不把原异常交给普通日志处理器。
            throw new ServiceUnavailableException();
        }
    }
}
