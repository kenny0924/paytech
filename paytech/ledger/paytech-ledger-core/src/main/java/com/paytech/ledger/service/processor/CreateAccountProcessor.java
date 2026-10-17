package com.paytech.ledger.service.processor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.account.api.rule.AccountIdentityRule;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.ledger.core.dal.dataobject.Account;
import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.core.enums.AccountStatus;
import com.paytech.ledger.domainservice.repository.ChartAccountRepository;
import com.paytech.ledger.domainservice.repository.LedgerBookRepository;
import com.paytech.ledger.domainservice.service.LedgerSetupService;
import com.paytech.ledger.service.processor.request.CreateAccountRequest;
import com.paytech.ledger.service.processor.response.CreatedResourceResponse;

import lombok.AllArgsConstructor;

/**
 * 在账簿下创建 ACTIVE 统一账户
 * <li>科目必须属于账簿采用的科目表且可过账；正常方向从科目复制并冻结。</li>
 * <li>科目、Owner 类型与余额桶组合按账户身份矩阵校验；账户身份唯一由唯一键保护。</li>
 */
@Component
@AllArgsConstructor
public class CreateAccountProcessor extends AbstractProcessor<CreateAccountRequest, CreatedResourceResponse> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final LedgerSetupService ledgerSetupService;
    private final LedgerBookRepository ledgerBookRepository;
    private final ChartAccountRepository chartAccountRepository;

    /** 仅依赖输入、首次与重放通用的附加校验。 */
    @Override
    protected void validate(CreateAccountRequest request) {
        AccountIdentityRule.checkOwnerId(request.getOwnerType(), request.getOwnerId());
        AccountIdentityRule.checkAccountIdentity(request.getAccountCode(), request.getOwnerType(),
            request.getBalanceBucket());
    }

    @Override
    protected CreatedResourceResponse processRequest(CreateAccountRequest request) {
        LedgerBook book = ledgerBookRepository.findById(request.getLedgerBookId());
        if (book == null) {
            throw new BizCheckFailException("账簿不存在: " + request.getLedgerBookId());
        }
        ChartAccount chartAccount = getPostableChartAccount(book, request.getAccountCode());
        Account account = buildAccount(request, chartAccount);
        ledgerSetupService.createAccount(account);
        CreatedResourceResponse response = new CreatedResourceResponse();
        response.setId(account.getId());
        return response;
    }

    /** 科目必须在账簿科目表中且允许直接过账。 */
    ChartAccount getPostableChartAccount(LedgerBook book, String accountCode) {
        ChartAccount chartAccount = chartAccountRepository.findByChartAndCode(book.getChartId(), accountCode);
        if (chartAccount == null || !Boolean.TRUE.equals(chartAccount.getPostingAllowed())) {
            throw new BizCheckFailException("科目不存在或不可过账: 账簿 " + book.getId() + ", 科目 " + accountCode);
        }
        return chartAccount;
    }

    /** 构建零余额账户。 */
    Account buildAccount(CreateAccountRequest request, ChartAccount chartAccount) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Account account = new Account();
        account.setId(idGenerator.nextId());
        account.setLedgerBookId(request.getLedgerBookId());
        account.setChartAccountId(chartAccount.getId());
        account.setOwnerType(request.getOwnerType());
        account.setOwnerId(request.getOwnerId());
        account.setBalanceBucket(request.getBalanceBucket());
        account.setNormalSide(chartAccount.getNormalSide());
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setDebitTotal(BigDecimal.ZERO);
        account.setCreditTotal(BigDecimal.ZERO);
        account.setNetBalance(BigDecimal.ZERO);
        account.setLastJournalSequence(0L);
        account.setBalanceVersion(0L);
        account.setCreateTime(now);
        account.setUpdateTime(now);
        return account;
    }
}
