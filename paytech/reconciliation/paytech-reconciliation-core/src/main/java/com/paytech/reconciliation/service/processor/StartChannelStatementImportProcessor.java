package com.paytech.reconciliation.service.processor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.ClearingAccountStatus;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.domain.Amounts;
import com.paytech.reconciliation.domain.view.ChannelStatementView;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementRepository;
import com.paytech.reconciliation.domainservice.repository.ClearingAccountRepository;
import com.paytech.reconciliation.domainservice.service.ChannelStatementService;
import com.paytech.reconciliation.service.processor.request.StartChannelStatementImportRequest;

import lombok.AllArgsConstructor;

/**
 * 开始导入渠道账单（UC-R06，#12，technique D-01）：建 RECEIVING 账单头
 * <li>清算账户须存在且 ACTIVE，账单币种须等于清算账户币种（设计 Q8），金额不超过币种精度。</li>
 * <li>更正重传（设计 Q11）：同一清算账户 + 批次号已有 RECONCILED / CASH_CONFIRMED 版本一律拒绝；有 RECEIVING / MATCHING 版本须先作废；
 * 否则 revision = 已有最大版本 + 1。并发开始同一批次由 uk(clearing_account_id, channel_batch_ref, revision) 兜底为 409，不加资源锁。</li>
 */
@Component
@AllArgsConstructor
public class StartChannelStatementImportProcessor
    extends AbstractProcessor<StartChannelStatementImportRequest, ChannelStatementView> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final ChannelStatementService channelStatementService;
    private final ClearingAccountRepository clearingAccountRepository;
    private final ChannelStatementRepository channelStatementRepository;

    @Override
    protected ChannelStatementView processRequest(StartChannelStatementImportRequest request) {
        // 业务验证
        ClearingAccount account = loadAccount(request);
        checkAmounts(request);
        int revision = nextRevision(account, request.getChannelBatchRef());

        // 本地保存
        ChannelStatement statement = build(request, account, revision);
        channelStatementService.start(statement);
        return new ChannelStatementView(statement, account);
    }

    /** 清算账户存在、ACTIVE 且币种一致。 */
    ClearingAccount loadAccount(StartChannelStatementImportRequest request) {
        ClearingAccount account = clearingAccountRepository.findByCode(request.getClearingAccountCode());
        if (account == null || account.getStatus() != ClearingAccountStatus.ACTIVE) {
            throw new BizCheckFailException("清算账户不存在或已停用: " + request.getClearingAccountCode());
        }
        if (!account.getCurrency().equals(request.getCurrency())) {
            throw new BizCheckFailException("账单币种必须等于清算账户币种: 清算账户 " + account.getClearingAccountCode()
                + " 币种 " + account.getCurrency() + ", 账单币种 " + request.getCurrency());
        }
        return account;
    }

    /** 账单头金额不超过币种精度；费用与净额非负。 */
    static void checkAmounts(StartChannelStatementImportRequest request) {
        if (request.getFeeAmount().signum() < 0 || request.getNetAmount().signum() < 0) {
            throw new BizCheckFailException("账单头费用与净额必须非负: 批次 " + request.getChannelBatchRef());
        }
        String label = "账单头 " + request.getChannelBatchRef();
        Amounts.checkPrecision(request.getCurrency(), request.getGrossAmount(), label + " 总额");
        Amounts.checkPrecision(request.getCurrency(), request.getFeeAmount(), label + " 费用");
        Amounts.checkPrecision(request.getCurrency(), request.getNetAmount(), label + " 净额");
    }

    /** 同批次已有版本的检查与下一版本号。 */
    int nextRevision(ClearingAccount account, String channelBatchRef) {
        List<ChannelStatement> versions = channelStatementRepository.listByBatch(account.getId(), channelBatchRef);
        String context = "清算账户 " + account.getClearingAccountCode() + ", 批次 " + channelBatchRef;
        for (ChannelStatement version : versions) {
            if (version.getStatus() == ChannelStatementStatus.RECONCILED
                || version.getStatus() == ChannelStatementStatus.CASH_CONFIRMED) {
                throw new BizCheckFailException("同一批次已对平，不能更正重传（差额走后续账单调整行）: " + context + ", 版本 "
                    + version.getRevision());
            }
            if (version.getStatus() == ChannelStatementStatus.RECEIVING
                || version.getStatus() == ChannelStatementStatus.MATCHING) {
                throw new BizCheckFailException("同一批次已有未作废的版本，须先作废: " + context + ", 版本 "
                    + version.getRevision() + ", 状态 " + version.getStatus().getCode());
            }
        }
        return versions.stream().mapToInt(ChannelStatement::getRevision).max().orElse(0) + 1;
    }

    /** RECEIVING 账单头。 */
    ChannelStatement build(StartChannelStatementImportRequest request, ClearingAccount account, int revision) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ChannelStatement statement = new ChannelStatement();
        statement.setId(idGenerator.nextId());
        statement.setClearingAccountId(account.getId());
        statement.setChannelBatchRef(request.getChannelBatchRef());
        statement.setRevision(revision);
        statement.setSettlementDate(request.getSettlementDate());
        statement.setCurrency(request.getCurrency());
        statement.setGrossAmount(request.getGrossAmount());
        statement.setFeeAmount(request.getFeeAmount());
        statement.setNetAmount(request.getNetAmount());
        statement.setCashDirection(request.getCashDirection());
        statement.setDeclaredLineCount(request.getDeclaredLineCount());
        statement.setImportedLineCount(0);
        statement.setFileName(request.getFileName());
        statement.setFileContentHash(request.getFileContentHash());
        statement.setStatus(ChannelStatementStatus.RECEIVING);
        statement.setCashConfirmedAmount(BigDecimal.ZERO);
        statement.setMakerId(request.getActorId());
        statement.setVersion(1L);
        statement.setCreateTime(now);
        statement.setUpdateTime(now);
        return statement;
    }
}
