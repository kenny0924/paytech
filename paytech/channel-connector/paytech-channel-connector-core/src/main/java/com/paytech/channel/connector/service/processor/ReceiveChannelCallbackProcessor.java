package com.paytech.channel.connector.service.processor;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.channel.connector.api.enums.ChannelCallbackErrorCode;
import com.paytech.channel.connector.api.enums.ChannelType;
import com.paytech.channel.connector.core.dal.dataobject.ChannelAccount;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCallback;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCallbackItem;
import com.paytech.channel.connector.core.enums.CallbackMappingStatus;
import com.paytech.channel.connector.core.enums.CallbackSignatureStatus;
import com.paytech.channel.connector.core.enums.CallbackStatus;
import com.paytech.channel.connector.core.enums.CallbackTargetType;
import com.paytech.channel.connector.domain.ChannelCallbackContext;
import com.paytech.channel.connector.domain.ChannelCallbackDefinition;
import com.paytech.channel.connector.domain.ChannelExecutionContext;
import com.paytech.channel.connector.domain.ChannelPaymentNotice;
import com.paytech.channel.connector.domainservice.repository.ChannelAccountRepository;
import com.paytech.channel.connector.domainservice.service.ChannelCallbackService;
import com.paytech.channel.connector.integration.ChannelAccountConfigReader;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.protocol.domain.ChannelIdentity;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelService;
import com.paytech.channel.connector.service.handler.ChannelPaymentNotifiedHandler;
import com.paytech.channel.connector.service.processor.request.ReceiveChannelCallbackRequest;
import com.paytech.channel.connector.service.processor.response.ReceiveChannelCallbackResponse;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 接收渠道支付结果通知（3DS 全链路 technique 3.2）：验签、去重入库，验签通过且映射到支付尝试时同事务登记 ChannelPaymentNotified 发布任务。
 *
 * <p>
 * 渠道须为启用的支付渠道且配置了通知定义，账号须属于该渠道且有 Vault 配置，否则 404、不入库。事务外经 Vault 取账号参数中的公钥，按定义以
 * SHA256withRSA 校验签名头对原始 Body 字节的签名。本地事务插入 t_channel_callback 与一条 t_channel_callback_item：
 * <li>验签通过：payload_fingerprint 为 Body 的 SHA-256；商户订单号能映射为支付尝试 ID 时 MAPPED 并登记发布任务、状态 RECEIVED，否则
 * UNMATCHED、状态 PROCESSED（仍应答成功，避免渠道无限重发，靠查单兜底）。同一账号同一指纹撞唯一索引视为重复通知，直接应答成功。</li>
 * <li>验签失败：signature_status、callback_status 均为 REJECTED，不写指纹（避免伪造通知占用真实通知的去重键）、不写回调项，入库后返回 401。</li>
 * <li>不保存原始 Body，日志只记 ID、渠道编码与结论；通知只作为 Payment 查单的信号，本服务不解释支付结果。</li>
 */
@Slf4j
@Component
@AllArgsConstructor
public class ReceiveChannelCallbackProcessor
    extends AbstractProcessor<ReceiveChannelCallbackRequest, ReceiveChannelCallbackResponse> {
    /** 支付结果通知的回调项事件类型。 */
    static final String PAYMENT_RESULT = "PAYMENT_RESULT";

    private final Clock clock;
    private final IdGenerator idGenerator;
    private final ChannelService channelService;
    private final ChannelAccountRepository accounts;
    private final ChannelAccountConfigReader configReader;
    private final TransactionTemplate transactionTemplate;
    private final ChannelCallbackService channelCallbackService;
    private final CompensationEventRegistrar compensationEventRegistrar;

    @Override
    protected ReceiveChannelCallbackResponse processRequest(ReceiveChannelCallbackRequest request) {
        // 定位渠道、账号与通知定义
        ChannelCallbackContext context = buildContext(request);

        // 事务外取公钥并验签
        boolean verified = verify(context, request);

        // 映射并入库
        buildCallback(context, request, verified);
        boolean saved = save(context);
        log.info("渠道通知已处理: channelCode={}, channelAccountId={}, callbackId={}, signature={}, mapping={}, duplicate={}",
            request.getChannelCode(), request.getChannelAccountId(), context.getCallback().getChannelCallbackId(),
            context.getCallback().getSignatureStatus().getCode(),
            context.getItem() == null ? null : context.getItem().getMappingStatus().getCode(), !saved);

        // 应答
        if (!verified) {
            throw new FailException(ChannelCallbackErrorCode.CHANNEL_CALLBACK_SIGNATURE_INVALID);
        }
        return new ReceiveChannelCallbackResponse(context.getDefinition().getAcknowledgementContentType(),
            context.getDefinition().getAcknowledgementBody());
    }

    /** 启用的支付渠道、属于该渠道且有 Vault 配置的账号、已配置的通知定义；任一不满足为 404。 */
    ChannelCallbackContext buildContext(ReceiveChannelCallbackRequest request) {
        ChannelIdentity channel = channelService.findChannelByCode(request.getChannelCode());
        if (channel == null || channel.getChannelType() != ChannelType.PAYMENT
            || channel.getChannelStatus() != ChannelStatus.ACTIVE || channel.getCallbackDefinitionJson() == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND, "渠道通知入口不可用");
        }
        ChannelAccount account = accounts.findById(request.getChannelAccountId());
        if (account == null || !Objects.equals(account.getChannelId(), channel.getChannelId())
            || account.getAccountVaultToken() == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND, "渠道通知入口不可用");
        }
        return new ChannelCallbackContext(channel, account, ChannelCallbackDefinition.parse(channel.getCallbackDefinitionJson()));
    }

    /** 经 Vault 取账号参数中的公钥并验签；公钥缺失属于配置错误。 */
    boolean verify(ChannelCallbackContext context, ReceiveChannelCallbackRequest request) {
        var definition = context.getDefinition();
        var configuration = configReader.read(new ChannelExecutionContext(context.getChannel(), context.getAccount(), null));
        String publicKey = configuration.getParameters().get(definition.getPublicKeyParameter());
        if (publicKey == null) {
            throw new ErrorException("渠道账号缺少通知验签公钥参数: " + context.getAccount().getChannelAccountId());
        }
        String signature = request.getHeaders().get(definition.getSignatureHeader().toLowerCase());
        return definition.verify(publicKey, request.getBody(), signature);
    }

    /** 按验签与映射结论构造回调信封与回调项。 */
    void buildCallback(ChannelCallbackContext context, ReceiveChannelCallbackRequest request, boolean verified) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var callback = new ChannelCallback();
        callback.setChannelCallbackId(idGenerator.nextId());
        callback.setChannelId(context.getChannel().getChannelId());
        callback.setChannelAccountId(context.getAccount().getChannelAccountId());
        callback.setSignatureStatus(verified ? CallbackSignatureStatus.VERIFIED : CallbackSignatureStatus.REJECTED);
        callback.setReceivedAt(now);
        callback.setVersion(1L);
        callback.setCreateTime(now);
        callback.setUpdateTime(now);
        context.setCallback(callback);
        if (!verified) {
            callback.setCallbackStatus(CallbackStatus.REJECTED);
            return;
        }
        callback.setPayloadFingerprint(sha256(request.getBody()));
        ChannelPaymentNotice notice = context.getDefinition().read(request.getBody());
        context.setItem(item(callback, notice, now));
        callback.setCallbackStatus(notice == null ? CallbackStatus.PROCESSED : CallbackStatus.RECEIVED);
    }

    /** 唯一一条回调项：映射到支付尝试，或无法映射。 */
    ChannelCallbackItem item(ChannelCallback callback, ChannelPaymentNotice notice, Instant now) {
        var item = new ChannelCallbackItem();
        item.setChannelCallbackItemId(idGenerator.nextId());
        item.setChannelCallbackId(callback.getChannelCallbackId());
        item.setItemNo(1);
        item.setEventType(PAYMENT_RESULT);
        item.setCreateTime(now);
        if (notice == null) {
            item.setTargetType(CallbackTargetType.UNKNOWN);
            item.setMappingStatus(CallbackMappingStatus.UNMATCHED);
            return item;
        }
        item.setTargetType(CallbackTargetType.PAYMENT_ATTEMPT);
        item.setTargetId(notice.getAttemptId());
        item.setExternalTransactionId(notice.getChannelTransactionRef());
        item.setNormalizedStatus(notice.getStatus());
        item.setChannelEventTime(notice.getEventTime());
        item.setMappingStatus(CallbackMappingStatus.MAPPED);
        return item;
    }

    /**
     * 本地事务保存信封、回调项与发布任务
     *
     * @return 是否新保存；同一账号同一指纹已存在（重复通知）为 false，事务整体回滚
     */
    boolean save(ChannelCallbackContext context) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                channelCallbackService.saveCallback(context);
                if (context.notifyPayment()) {
                    compensationEventRegistrar
                        .register(ChannelPaymentNotifiedHandler.buildEvent(context.getCallback().getChannelCallbackId()));
                }
            });
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    /** 原始 Body 的 SHA-256 小写十六进制。 */
    static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new ErrorException("SHA-256 不可用");
        }
    }
}
