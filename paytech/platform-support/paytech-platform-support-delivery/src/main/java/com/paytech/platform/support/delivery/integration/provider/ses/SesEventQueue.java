package com.paytech.platform.support.delivery.integration.provider.ses;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationProvider;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

/**
 * 拉取账号事件队列中的 SES 投递事件
 * <li>访问队列复用账号的发送密钥，每轮从 Vault 读取一次，不缓存原值</li>
 * <li>SQS 客户端按队列区域复用、不持有凭据、关闭 SDK 重试；失败只返回有界错误码，原异常可能含队列信息，不外抛</li>
 */
@RequiredArgsConstructor
public class SesEventQueue implements AutoCloseable {

    /** 账号事件队列 URL 的格式；区域取自主机名。 */
    public static final Pattern QUEUE_URL =
        Pattern.compile("https://sqs\\.([a-z]{2}(?:-[a-z]+)+-[0-9])\\.amazonaws\\.com/[0-9]{12}/[A-Za-z0-9_-]{1,80}");

    /** 单批最多消息数（SQS 上限）。 */
    public static final int BATCH_SIZE = 10;

    private static final Set<String> CREDENTIAL_ERRORS = Set.of("AccessDenied", "AccessDeniedException",
        "InvalidClientTokenId", "SignatureDoesNotMatch", "UnrecognizedClientException", "IncompleteSignature",
        "MissingAuthenticationToken", "ExpiredTokenException", "InvalidSecurity");

    private static final Set<String> THROTTLING_ERRORS = Set.of("RequestThrottled", "ThrottlingException",
        "OverLimit", "KmsThrottled");

    private final SesEventProperties properties;

    private final SesCredentialVault credentials;

    private final Map<String, SqsClient> clients = new ConcurrentHashMap<>();

    /**
     * 事务外读取账号密钥，返回只用于本轮的接收器
     * <li>Vault 不可用或值不合法时原样上抛，调用方记为凭据不可用</li>
     *
     * @param account 已配置事件队列 URL 与密钥 Token 的账号
     */
    public SesEventReceiver prepare(NotificationProvider account) {
        AwsCredentialsProvider credential = credentials.reveal(account.getCredentialVaultToken());
        return new SesEventReceiver(this, account.getEventQueueUrl(), credential);
    }

    /**
     * 接收一批消息，不重试
     *
     * @param queueUrl 账号事件队列 URL
     * @param credential 本轮凭据
     * @return 消息列表；URL 不合法或 SQS 失败时为错误码
     */
    SesEventBatch receive(String queueUrl, AwsCredentialsProvider credential) {
        SqsClient client = client(queueUrl);
        if (client == null) {
            return new SesEventBatch(List.of(), "SQS_CLIENT_ERROR");
        }
        var request = ReceiveMessageRequest.builder().queueUrl(queueUrl).maxNumberOfMessages(BATCH_SIZE)
            .waitTimeSeconds((int) properties.getWaitTime().toSeconds())
            .visibilityTimeout((int) properties.getVisibilityTimeout().toSeconds())
            .overrideConfiguration(config -> config.credentialsProvider(credential)).build();
        try {
            var messages = client.receiveMessage(request).messages().stream()
                .map(m -> new SesEventMessage(m.body(), m.receiptHandle())).toList();
            return new SesEventBatch(messages, null);
        } catch (SdkException failure) {
            return new SesEventBatch(List.of(), classify(failure));
        }
    }

    /**
     * 删除已处理的消息，不重试
     *
     * @return 成功为空，失败为错误码；消息未删除会在可见性超时后再次到达
     */
    String delete(String queueUrl, String receiptHandle, AwsCredentialsProvider credential) {
        SqsClient client = client(queueUrl);
        if (client == null) {
            return "SQS_CLIENT_ERROR";
        }
        var request = DeleteMessageRequest.builder().queueUrl(queueUrl).receiptHandle(receiptHandle)
            .overrideConfiguration(config -> config.credentialsProvider(credential)).build();
        try {
            client.deleteMessage(request);
            return null;
        } catch (SdkException failure) {
            return classify(failure);
        }
    }

    /**
     * SQS 失败的有界分类
     * <li>凭据或权限被拒、队列不存在、限流各自成类；网络、超时与 5xx 为不可用；其余 4xx 为客户端错误</li>
     */
    static String classify(SdkException failure) {
        if (failure instanceof QueueDoesNotExistException) {
            return "SQS_QUEUE_NOT_FOUND";
        }
        if (!(failure instanceof SqsException rejected)) {
            return "SQS_UNAVAILABLE";
        }
        String errorCode = rejected.awsErrorDetails() == null || rejected.awsErrorDetails().errorCode() == null ? ""
            : rejected.awsErrorDetails().errorCode();
        if (rejected.statusCode() == 403 || CREDENTIAL_ERRORS.contains(errorCode)) {
            return "SQS_ACCESS_DENIED";
        }
        if (errorCode.endsWith("NonExistentQueue")) {
            return "SQS_QUEUE_NOT_FOUND";
        }
        if (THROTTLING_ERRORS.contains(errorCode)) {
            return "SQS_THROTTLED";
        }
        return rejected.statusCode() >= 500 ? "SQS_UNAVAILABLE" : "SQS_CLIENT_ERROR";
    }

    /** 队列 URL 所在区域的客户端；URL 不合法返回空。 */
    SqsClient client(String queueUrl) {
        Matcher matcher = queueUrl == null ? null : QUEUE_URL.matcher(queueUrl);
        if (matcher == null || !matcher.matches()) {
            return null;
        }
        return clients.computeIfAbsent(matcher.group(1), this::create);
    }

    /**
     * 构造区域客户端
     * <li>JDK HttpURLConnection 传输；整次调用不超过连接加读超时，读超时已校验大于长轮询等待</li>
     * <li>客户端级凭据直接拒绝，密钥只能由每次请求提供</li>
     */
    SqsClient create(String region) {
        var http = UrlConnectionHttpClient.builder().connectionTimeout(properties.getConnectTimeout())
            .socketTimeout(properties.getReadTimeout());
        var builder = SqsClient.builder().region(Region.of(region))
            .credentialsProvider(() -> {
                throw SdkClientException.create("SQS 凭据须在每次请求前由 Vault 提供");
            })
            .httpClient(http.build())
            .overrideConfiguration(override -> override.retryStrategy(AwsRetryStrategy.doNotRetry())
                .apiCallTimeout(properties.getConnectTimeout().plus(properties.getReadTimeout())));
        if (properties.getEndpoint() != null) {
            builder.endpointOverride(properties.getEndpoint());
        }
        return builder.build();
    }

    /** 关闭全部已创建的客户端。 */
    @Override
    public void close() {
        clients.values().forEach(SqsClient::close);
        clients.clear();
    }
}
