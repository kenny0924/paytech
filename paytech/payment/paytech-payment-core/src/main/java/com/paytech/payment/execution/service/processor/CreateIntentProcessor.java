package com.paytech.payment.execution.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.payment.execution.core.constant.Locks;
import com.paytech.payment.execution.core.dal.dataobject.PaymentIntent;
import com.paytech.payment.execution.core.enums.IntentStatus;
import com.paytech.payment.execution.domain.CreateIntentContext;
import com.paytech.payment.execution.domainservice.repository.PaymentIntentRepository;
import com.paytech.payment.execution.service.PaymentExecutionService;
import com.paytech.payment.execution.service.processor.request.CreateIntentRequest;
import com.paytech.payment.execution.service.processor.response.CreateIntentResponse;

import lombok.AllArgsConstructor;

/** 创建支付意图 */
@Component
@AllArgsConstructor
public class CreateIntentProcessor extends AbstractLockProcessor<CreateIntentRequest, CreateIntentResponse> {
    final Clock clock;
    final PaymentIntentRepository paymentIntentRepository;
    final PaymentExecutionService paymentExecutionService;

    /** 等待获取来源业务资源锁。 */
    @Override
    protected LockCommand lockCommand(CreateIntentRequest request) {
        return LockCommand.lock(Locks.getIntentSourceLock(request.getSourceType(), request.getSourceId()));
    }

    @Override
    protected void validate(CreateIntentRequest request) {
        super.validate(request);
        if ((request.getPartnerShareVersionId() == null) != (request.getPlatformChargeVersionId() == null)) {
            throw new BizCheckFailException("合作伙伴分成版本与平台服务费版本必须同时提供");
        }
    }

    @Override
    protected CreateIntentResponse processWithinLock(CreateIntentRequest request) {
        // 查询原提交
        CreateIntentContext context = checkAndGetContext(request);
        if (context != null) {
            return processResponse(context);
        }

        // 构建上下文
        context = buildCreateIntentContext(request);

        // 保存上下文
        paymentExecutionService.saveCreateIntentContext(context);

        // 响应处理
        return processResponse(context);
    }

    /** 查询原意图并检查冻结信息 */
    CreateIntentContext checkAndGetContext(CreateIntentRequest request) {
        PaymentIntent intent =
            paymentIntentRepository.findIntentBySource(request.getSourceType(), request.getSourceId());
        if (intent == null) {
            return null;
        }
        if (!Objects.equals(intent.getClientId(), request.getClientId())) {
            throw new ConcurrentRequestException("来源订单已被其他 Client 使用");
        }
        if (!Objects.equals(intent.getCurrency(), request.getAmount().getCurrency())
            || intent.getAmount().compareTo(request.getAmount().getAmount()) != 0) {
            throw new ConcurrentRequestException("原支付意图不可修改金额或币种");
        }
        if (!Objects.equals(intent.getEntityNo(), request.getEntityNo())
            || !Objects.equals(intent.getClientPricingVersionId(), request.getClientPricingVersionId())
            || !Objects.equals(intent.getPartnerShareVersionId(), request.getPartnerShareVersionId())
            || !Objects.equals(intent.getPlatformChargeVersionId(), request.getPlatformChargeVersionId())
            || !Objects.equals(intent.getIndustryCode(), request.getIndustryCode())
            || !Objects.equals(intent.getMccCode(), request.getMccCode())
            || !Objects.equals(intent.getConsumerCountryCode(), request.getConsumerCountryCode())
            || !Objects.equals(intent.getExpiresAt(), request.getExpiresAt())) {
            throw new ConcurrentRequestException("来源订单的冻结信息已变更");
        }
        return new CreateIntentContext(intent);
    }

    /** 构建创建上下文 */
    CreateIntentContext buildCreateIntentContext(CreateIntentRequest request) {
        if (!request.getExpiresAt().isAfter(clock.instant())) {
            throw new BizCheckFailException("支付意图到期时间必须晚于当前时间");
        }
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        PaymentIntent intent = new PaymentIntent();
        intent.setClientId(request.getClientId());
        intent.setSourceType(request.getSourceType());
        intent.setSourceId(request.getSourceId());
        intent.setEntityNo(request.getEntityNo());
        intent.setClientPricingVersionId(request.getClientPricingVersionId());
        intent.setPartnerShareVersionId(request.getPartnerShareVersionId());
        intent.setPlatformChargeVersionId(request.getPlatformChargeVersionId());
        intent.setIndustryCode(request.getIndustryCode());
        intent.setMccCode(request.getMccCode());
        intent.setConsumerCountryCode(request.getConsumerCountryCode());
        intent.setAmount(request.getAmount().getAmount());
        intent.setCurrency(request.getAmount().getCurrency());
        intent.setExpiresAt(request.getExpiresAt());
        intent.setIntentStatus(IntentStatus.OPEN);
        intent.setVersion(1L);
        intent.setCreateTime(now);
        intent.setUpdateTime(now);
        return new CreateIntentContext(intent);
    }

    /** 响应处理 */
    CreateIntentResponse processResponse(CreateIntentContext context) {
        PaymentIntent intent = context.getIntent();
        CreateIntentResponse response = new CreateIntentResponse();
        response.setIntentId(intent.getPaymentIntentId());
        response.setStatus(intent.getIntentStatus());
        return response;
    }
}
