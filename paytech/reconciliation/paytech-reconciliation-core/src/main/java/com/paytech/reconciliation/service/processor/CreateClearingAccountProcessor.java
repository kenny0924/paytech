package com.paytech.reconciliation.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.money.Money;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.reconciliation.api.enums.ClearingAccountStatus;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.domain.ClearingAccountRules;
import com.paytech.reconciliation.domain.view.ClearingAccountView;
import com.paytech.reconciliation.domainservice.repository.CashAccountRepository;
import com.paytech.reconciliation.domainservice.repository.ClearingAccountRepository;
import com.paytech.reconciliation.domainservice.service.ClearingAccountService;
import com.paytech.reconciliation.service.processor.request.CreateClearingAccountRequest;

import lombok.AllArgsConstructor;

/**
 * 新建清算账户（UC-R01，#1）：ACTIVE、无 MID
 * <li>编码已存在为业务拒绝；唯一键冲突（并发同编码）由 Repository 转为 409 兜底。不加资源锁。</li>
 * <li>到账银行账户可为空；非空时必须是同经营主体、同币种、用途 CHANNEL_SETTLEMENT 的 ACTIVE 账户。容差不超过币种精度。</li>
 */
@Component
@AllArgsConstructor
public class CreateClearingAccountProcessor extends AbstractProcessor<CreateClearingAccountRequest, ClearingAccountView> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final CashAccountRepository cashAccountRepository;
    private final ClearingAccountService clearingAccountService;
    private final ClearingAccountRepository clearingAccountRepository;

    @Override
    protected ClearingAccountView processRequest(CreateClearingAccountRequest request) {
        // 业务验证
        if (clearingAccountRepository.findByCode(request.getClearingAccountCode()) != null) {
            throw new BizCheckFailException("清算账户编码已存在: " + request.getClearingAccountCode());
        }
        Money.of(request.getCurrency(), request.getCostToleranceAmount());
        if (request.getSettlementCashAccountId() != null) {
            ClearingAccountRules.checkSettlementCashAccount(request.getSettlementCashAccountId(),
                cashAccountRepository.findById(request.getSettlementCashAccountId()), request.getEntityNo(),
                request.getCurrency());
        }

        // 本地保存
        ClearingAccount account = build(request, clock.instant().truncatedTo(ChronoUnit.MICROS));
        clearingAccountService.create(account);
        return new ClearingAccountView(account, List.of());
    }

    /** 由命令构造 ACTIVE 清算账户。 */
    ClearingAccount build(CreateClearingAccountRequest request, Instant now) {
        ClearingAccount account = new ClearingAccount();
        account.setId(idGenerator.nextId());
        account.setClearingAccountCode(request.getClearingAccountCode());
        account.setChannelCode(request.getChannelCode());
        account.setEntityNo(request.getEntityNo());
        account.setCurrency(request.getCurrency());
        account.setSettlementCashAccountId(request.getSettlementCashAccountId());
        account.setUnmatchedWaitDays(request.getUnmatchedWaitDays());
        account.setCostToleranceAmount(request.getCostToleranceAmount());
        account.setCashGraceDays(request.getCashGraceDays());
        account.setStatus(ClearingAccountStatus.ACTIVE);
        account.setVersion(1L);
        account.setCreateTime(now);
        account.setUpdateTime(now);
        return account;
    }
}
