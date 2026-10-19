package com.paytech.account.domainservice.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.account.api.enums.BalanceMovementErrorCode;
import com.paytech.account.core.dal.dataobject.AccountPosting;
import com.paytech.account.core.dal.dataobject.AccountPostingStream;
import com.paytech.account.core.dal.dataobject.BalanceMovement;
import com.paytech.account.core.dal.dataobject.OwnerAccount;
import com.paytech.account.core.enums.AccountStatus;
import com.paytech.account.domain.MovementContext;
import com.paytech.account.domain.model.BalanceMovementResult;
import com.paytech.account.domainservice.process.AccountPostingWriter;
import com.paytech.account.domainservice.repository.AccountPostingRepository;
import com.paytech.account.domainservice.repository.BalanceMovementRepository;
import com.paytech.account.domainservice.repository.OwnerAccountRepository;
import com.paytech.account.domainservice.service.BalanceMovementService;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;

import lombok.AllArgsConstructor;

/**
 * 余额桶移动
 * <li>锁顺序与事实入账一致：入账流行 → Owner 账户按主键升序；转出账户必须已存在，转入账户不存在时按目录开立。</li>
 * <li>余额检查在锁定后进行，写入时仍以 balance_version CAS 保护；同来源并发首次请求由唯一键拦截为 409。</li>
 */
@Service
@AllArgsConstructor
public class BalanceMovementServiceImpl implements BalanceMovementService {
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final AccountPostingWriter accountPostingWriter;
    private final OwnerAccountRepository ownerAccountRepository;
    private final AccountPostingRepository accountPostingRepository;
    private final BalanceMovementRepository balanceMovementRepository;

    /** 同一事务内查原移动、锁定、检查并写入。 */
    @Override
    public BalanceMovementResult move(MovementContext context) {
        return transactionTemplate.execute(status -> {
            BalanceMovement existing = balanceMovementRepository.findBySource(context.getSourceType(),
                context.getSourceRef());
            if (existing != null) {
                return replay(existing);
            }
            Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            AccountPostingStream stream = accountPostingWriter.lockStream(context.getEntityType(), context.getEntityId(),
                context.getCurrency(), now);
            OwnerAccount from = ownerAccountRepository.findByIdentity(context.getEntityType(), context.getEntityId(),
                context.getCurrency(), context.getAccountType().getCode(), context.getAccountType().getOwnerType(),
                context.getOwnerId(), context.getSourceType().getFromBucket());
            if (from == null) {
                throw new FailException(BalanceMovementErrorCode.ACCOUNT_NOT_FOUND);
            }
            requireActive(from);
            OwnerAccount to = accountPostingWriter.resolveAccount(context.getEntityType(), context.getEntityId(),
                context.getCurrency(), context.getAccountType(), context.getOwnerId(),
                context.getSourceType().getToBucket(), now);
            requireActive(to);
            requireSufficient(context, from, to);
            BalanceMovement movement = newMovement(context);
            AccountPosting posting = accountPostingWriter.postMovement(stream, movement, from, to, now);
            return new BalanceMovementResult(movement, posting, false);
        });
    }

    /** 锁定两个账户后检查转出桶余额足以覆盖移动金额。 */
    void requireSufficient(MovementContext context, OwnerAccount from, OwnerAccount to) {
        List<OwnerAccount> locked = ownerAccountRepository.lockByIds(List.of(Math.min(from.getId(), to.getId()),
            Math.max(from.getId(), to.getId())));
        OwnerAccount current = locked.stream().filter(account -> account.getId().equals(from.getId())).findFirst()
            .orElseThrow(() -> new ErrorException("转出账户在锁定时不存在: " + from.getId()));
        if (current.getNetBalance().compareTo(context.getAmount()) < 0) {
            throw new FailException(BalanceMovementErrorCode.BALANCE_INSUFFICIENT);
        }
    }

    /** 冻结或关闭的账户拒绝命令。 */
    static void requireActive(OwnerAccount account) {
        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new FailException(BalanceMovementErrorCode.ACCOUNT_NOT_ACTIVE);
        }
    }

    /** 同来源已移动：返回原移动与批次。 */
    BalanceMovementResult replay(BalanceMovement movement) {
        AccountPosting posting = accountPostingRepository.findById(movement.getPostingId());
        if (posting == null) {
            throw new ErrorException("余额桶移动缺少入账批次: " + movement.getId());
        }
        return new BalanceMovementResult(movement, posting, true);
    }

    /** 由命令填写来源、账户身份、桶与金额。 */
    static BalanceMovement newMovement(MovementContext context) {
        BalanceMovement movement = new BalanceMovement();
        movement.setEntityType(context.getEntityType());
        movement.setEntityId(context.getEntityId());
        movement.setCurrency(context.getCurrency());
        movement.setAccountCode(context.getAccountType().getCode());
        movement.setOwnerType(context.getAccountType().getOwnerType());
        movement.setOwnerId(context.getOwnerId());
        movement.setFromBucket(context.getSourceType().getFromBucket());
        movement.setToBucket(context.getSourceType().getToBucket());
        movement.setAmount(context.getAmount());
        movement.setSourceType(context.getSourceType());
        movement.setSourceRef(context.getSourceRef());
        movement.setOperatorId(context.getOperatorId());
        return movement;
    }
}
