package com.paytech.platform.support.delivery.integration.provider.ses;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.delivery.core.constant.NotificationProviderTypes;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationProvider;
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
 * <li>账号的区域、发件地址与 Configuration Set 随调用传入；密钥在 prepare 中从 Vault 逐次读取，只用于该次请求签名</li>
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

    private final SesClients clients;
    private final SesCredentialVault credentials;

    /** Amazon SES。 */
    @Override
    public String providerType() {
        return NotificationProviderTypes.AMAZON_SES;
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
    public EmailProviderResult send(NotificationProvider account, EmailProviderRequest request) {
        return prepare(account).send(request);
    }

    /**
     * 事务外从 Vault 读取账号密钥，返回只用于本次的发送器
     * <li>账号配置不完整、Vault 不可用或值不合法时抛出，调用方此时尚未登记尝试</li>
     */
    @Override
    public EmailSender prepare(NotificationProvider account) {
        if (!account.missingConfiguration().isEmpty()) {
            throw new ServiceUnavailableException();
        }
        AwsCredentialsProvider credential = credentials.reveal(account.getCredentialVaultToken());
        SesV2Client client = clients.client(account.getRegion());
        return request -> send(client, sendEmailRequest(account, request, credential));
    }

    /**
     * 调用 SendEmail 一次，不重试。
     *
     * @param client 账号区域的客户端
     * @param request 已带本次凭据的请求
     * @return 受理带 MessageId；SES 明确拒绝按错误类型分类；网络/超时/5xx/缺少 MessageId 为 UNKNOWN
     */
    EmailProviderResult send(SesV2Client client, SendEmailRequest request) {
        try {
            String messageId = client.sendEmail(request).messageId();
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
    public EmailProviderResult query(NotificationProvider account, String requestNo, String messageId) {
        throw new ErrorException("SES 不支持按原请求查询发送结果");
    }

    /** 构造 SendEmail 请求：账号发件地址、单一收件人、UTF-8 纯文本、可选 Configuration Set、请求号标签及本次凭据。 */
    static SendEmailRequest sendEmailRequest(NotificationProvider account, EmailProviderRequest request,
        AwsCredentialsProvider credential) {
        String charset = StandardCharsets.UTF_8.name();
        var message = Message.builder().subject(Content.builder().data(request.getSubject()).charset(charset).build())
            .body(Body.builder().text(Content.builder().data(request.getBody()).charset(charset).build()).build())
            .build();
        return SendEmailRequest.builder().fromEmailAddress(account.getFromAddress())
            .destination(Destination.builder().toAddresses(request.getEmail()).build())
            .content(EmailContent.builder().simple(message).build())
            .configurationSetName(account.getConfigurationSet())
            .emailTags(MessageTag.builder().name(REQUEST_NO_TAG).value(request.getRequestNo()).build())
            .overrideConfiguration(config -> config.credentialsProvider(credential)).build();
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
