package com.paytech.account.service.processor;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.paytech.account.api.enums.BalanceMovementErrorCode;
import com.paytech.account.api.enums.OwnerAccountType;
import com.paytech.account.domain.MovementContext;
import com.paytech.account.domain.model.BalanceMovementResult;
import com.paytech.account.domainservice.service.BalanceMovementService;
import com.paytech.account.domainservice.validator.EntityIdentityValidator;
import com.paytech.account.service.processor.request.MoveBalanceRequest;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 余额桶移动命令（{@code POST /balance-movements/v1}，technique 2.2）
 * <li>事务外校验输入：来源类型允许经接口发起、桶方向符合来源类型、科目与 Owner 类型及两个桶符合 Owner 账户类型目录，否则 MOVEMENT_NOT_ALLOWED；
 * 金额为正、币种与请求一致，主体标识与主体类型一致，否则参数错误。这些判断只依赖输入，首次与重放都适用。</li>
 * <li>业务操作身份为 (sourceType, sourceRef)：BalanceMovementService 在一个短事务内先查原移动，命中即返回 replayed，不比较内容也不重新执行。</li>
 * <li>没有外部依赖与提交后推进；不使用资源锁，同流与同账户并发由入账流行锁、余额 CAS 与来源唯一键保护。</li>
 */
@Component
@AllArgsConstructor
public class MoveBalanceProcessor extends AbstractProcessor<MoveBalanceRequest, BalanceMovementResult> {
    private final BalanceMovementService balanceMovementService;

    @Override
    protected void validate(MoveBalanceRequest request) {
        EntityIdentityValidator.requireConsistent(request.getEntityType(), request.getEntityId());
        if (!request.getCurrency().equals(request.getAmount().getCurrency()) || !request.getAmount().isPositive()) {
            throw new InvalidParameterException("移动金额必须为正且币种与请求一致");
        }
        if (!request.getSourceType().isApiAllowed()
            || !request.getSourceType().allows(request.getFromBucket(), request.getToBucket())) {
            throw new FailException(BalanceMovementErrorCode.MOVEMENT_NOT_ALLOWED);
        }
        OwnerAccountType type = OwnerAccountType.ofCode(request.getAccountCode());
        if (type == null || !type.allows(request.getOwnerType(), request.getFromBucket())
            || !type.allows(request.getOwnerType(), request.getToBucket())) {
            throw new FailException(BalanceMovementErrorCode.MOVEMENT_NOT_ALLOWED);
        }
    }

    @Override
    protected BalanceMovementResult processRequest(MoveBalanceRequest request) {
        BigDecimal amount = request.getAmount().getAmount();
        return balanceMovementService.move(new MovementContext(request.getSourceType(), request.getSourceRef(),
            request.getEntityType(), request.getEntityId(), request.getCurrency(),
            OwnerAccountType.ofCode(request.getAccountCode()), request.getOwnerId(), amount, request.getOperatorId()));
    }
}
