package com.paytech.platform.support.delivery.integration.provider.ses;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.delivery.integration.provider.EmailProvider;
import com.paytech.platform.support.delivery.integration.provider.EmailProviderRequest;
import com.paytech.platform.support.delivery.integration.provider.EmailProviderResult;
import com.paytech.platform.support.delivery.integration.provider.EmailProviderResult.Outcome;
import com.paytech.platform.support.delivery.integration.provider.EmailSender;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;

/**
 * Amazon SES v2 发送适配器；SES 不支持按我方请求号查询，按至少发一次处理。
 * <li>每次只发一个收件人的纯文本邮件，请求号以 EmailTag 携带，供后续事件回调关联</li>
 * <li>配置 Vault 凭据时在 prepare 中逐次读取，只用于该次请求签名；未配置时使用客户端默认凭据链</li>
 * <li>只返回有界结果码；收件人、正文和 SES 原始错误文字不进入结果、异常或日志</li>
 */
@RequiredArgsConstructor
public class SesEmailProvider implements EmailProvider {

    /** 携带我方请求号的 EmailTag 名称。 */
    public static final String REQUEST_NO_TAG = "notification_request_no";

    /** SES 拒绝凭据或签名的错误码；换密钥或权限修复后可能成功，按可重试处理。 */
    static final Set<String> CREDENTIAL_ERRORS = Set.of("UnrecognizedClientException", "InvalidClientTokenId",
        "SignatureDoesNotMatch", "InvalidSignatureException", "IncompleteSignature", "MissingAuthenticationToken",
        "ExpiredTokenException", "AccessDeniedException");

    private final SesV2Client client;
    private final SesEmailProperties properties;

    /** Vault 凭据读取器；为空时使用客户端默认凭据链。 */
    private final SesCredentialVault credentials;

    /** 发送账号编码来自部署配置。 */
    @Override
    public String providerCode() {
        return properties.getProviderCode();
    }

    /** SES 没有按客户端请求号查询结果的接口。 */
    @Override
    public boolean supportsQuery() {
        return false;
    }

    /** 请求号由尝试主键派生，外发前已持久化。 */
    @Override
    public String requestNo(long attemptId) {
        return "ses-" + attemptId;
    }

    /** 读取本次凭据后发送；直接调用时同样先读取凭据。 */
    @Override
    public EmailProviderResult send(EmailProviderRequest request) {
        return prepare().send(request);
    }

    /** 事务外从 Vault 读取本次凭据；Vault 不可用或值不合法时抛出，调用方此时尚未登记尝试。 */
    @Override
    public EmailSender prepare() {
        AwsCredentialsProvider override = credentials == null ? null : credentials.reveal();
        return request -> send(request, override);
    }

    /**
     * 调用 SendEmail 一次，不重试。
     *
     * @param request 单收件人纯文本邮件
     * @param override 本次请求的凭据；为空时使用客户端默认凭据链
     * @return 受理带 MessageId；SES 明确拒绝按错误类型分类；网络/超时/5xx/缺少 MessageId 为 UNKNOWN
     */
    EmailProviderResult send(EmailProviderRequest request, AwsCredentialsProvider override) {
        try {
            String messageId = client.sendEmail(sendEmailRequest(request, override)).messageId();
            if (messageId == null || messageId.isBlank()) {
                return result(Outcome.UNKNOWN, "SES_RESULT_UNKNOWN");
            }
            return new EmailProviderResult(Outcome.ACCEPTED, messageId, "SES_ACCEPTED");
        } catch (SesV2Exception rejected) {
            // 原异常可能带收件人或 SES 原文，只保留分类结果。
            return classify(rejected);
        } catch (SdkException uncertain) {
            return result(Outcome.UNKNOWN, "SES_RESULT_UNKNOWN");
        }
    }

    /** 不支持查询；状态机只在 supportsQuery 为 true 时调用，到达这里说明调用方违反合同。 */
    @Override
    public EmailProviderResult query(String requestNo, String messageId) {
        throw new ErrorException("SES 不支持按原请求查询发送结果");
    }

    /** 构造 SendEmail 请求：单一收件人、UTF-8 纯文本、可选 Configuration Set、请求号标签及本次凭据。 */
    SendEmailRequest sendEmailRequest(EmailProviderRequest request, AwsCredentialsProvider override) {
        String charset = StandardCharsets.UTF_8.name();
        var message = Message.builder().subject(Content.builder().data(request.getSubject()).charset(charset).build())
            .body(Body.builder().text(Content.builder().data(request.getBody()).charset(charset).build()).build())
            .build();
        var builder = SendEmailRequest.builder().fromEmailAddress(properties.getFromAddress())
            .destination(Destination.builder().toAddresses(request.getEmail()).build())
            .content(EmailContent.builder().simple(message).build())
            .configurationSetName(properties.getConfigurationSet())
            .emailTags(MessageTag.builder().name(REQUEST_NO_TAG).value(request.getRequestNo()).build());
        if (override != null) {
            builder.overrideConfiguration(config -> config.credentialsProvider(override));
        }
        return builder.build();
    }

    /**
     * SES 明确返回的错误分类。
     * <li>账号、配额、节流与发件配置错误：未受理，可换账号或稍后重试</li>
     * <li>内容或请求本身被拒：重试不会成功</li>
     * <li>凭据或签名被拒：未受理，换密钥或修复权限后可重试</li>
     * <li>5xx：SES 可能已处理，结果未知</li>
     */
    static EmailProviderResult classify(SesV2Exception failure) {
        return switch (failure) {
            case TooManyRequestsException _ -> result(Outcome.RETRYABLE_REJECTED, "SES_THROTTLED");
            case LimitExceededException _ -> result(Outcome.RETRYABLE_REJECTED, "SES_QUOTA_EXCEEDED");
            case SendingPausedException _ -> result(Outcome.RETRYABLE_REJECTED, "SES_ACCOUNT_PAUSED");
            case AccountSuspendedException _ -> result(Outcome.RETRYABLE_REJECTED, "SES_ACCOUNT_SUSPENDED");
            case MailFromDomainNotVerifiedException _ -> result(Outcome.RETRYABLE_REJECTED, "SES_SENDER_NOT_VERIFIED");
            case NotFoundException _ -> result(Outcome.RETRYABLE_REJECTED, "SES_CONFIGURATION_NOT_FOUND");
            case MessageRejectedException _ -> result(Outcome.REJECTED, "SES_MESSAGE_REJECTED");
            case BadRequestException _ -> result(Outcome.REJECTED, "SES_BAD_REQUEST");
            default -> unclassified(failure);
        };
    }

    /** 其余错误：凭据类可重试，5xx 未知，其他 4xx 视为请求被拒。 */
    static EmailProviderResult unclassified(SesV2Exception failure) {
        String errorCode = failure.awsErrorDetails() == null ? null : failure.awsErrorDetails().errorCode();
        if (errorCode != null && CREDENTIAL_ERRORS.contains(errorCode)) {
            return result(Outcome.RETRYABLE_REJECTED, "SES_CREDENTIAL_REJECTED");
        }
        return failure.statusCode() >= 500 ? result(Outcome.UNKNOWN, "SES_SERVER_ERROR")
            : result(Outcome.REJECTED, "SES_CLIENT_ERROR");
    }

    /** 没有 MessageId 的有界结果。 */
    static EmailProviderResult result(Outcome outcome, String code) {
        return new EmailProviderResult(outcome, null, code);
    }
}
