package com.paytech.reconciliation.service.processor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.reconciliation.api.enums.ClearingAccountStatus;
import com.paytech.reconciliation.api.enums.ReconEntityType;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.domain.ChannelStatementLines;
import com.paytech.reconciliation.domain.OpenReconExceptionCommand;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementLineRepository;
import com.paytech.reconciliation.domainservice.repository.ClearingAccountRepository;
import com.paytech.reconciliation.domainservice.service.ChannelStatementService;
import com.paytech.reconciliation.domainservice.service.ReconExceptionService;
import com.paytech.reconciliation.service.processor.request.ExpireWaitingChannelLinesRequest;
import com.paytech.reconciliation.service.processor.response.ExpireWaitingChannelLinesResponse;

import lombok.AllArgsConstructor;

/**
 * 账单行等待期满检查（XXL 任务 channelLineWaitExpiry，需求 Q-04，设计 F1 规则 4）
 * <li>逐个 ACTIVE 清算账户，读取 MATCHING 账单中接收时间早于「当前 − unmatched_wait_days」仍 UNMATCHED 的交易行，每行一个短事务：开
 * CHANNEL_LINE_UNMATCHED（差异为该行带符号净额贡献）并把行转为 EXCEPTION。</li>
 * <li>幂等：只读 UNMATCHED 行，处理后行变为 EXCEPTION；同一行同类型未结异常由 active_slot 唯一。不加账单锁：行版本条件更新与匹配任务互斥，
 * 冲突时本行回滚并计为跳过（匹配任务已匹配该行，或下一轮再处理）。</li>
 */
@Component
@AllArgsConstructor
public class ExpireWaitingChannelLinesProcessor
    extends AbstractProcessor<ExpireWaitingChannelLinesRequest, ExpireWaitingChannelLinesResponse> {
    /** 每个清算账户每轮最多处理的行数。 */
    static final int BATCH_LIMIT = 1000;

    /** 清算账户分页条数。 */
    static final int ACCOUNT_PAGE_SIZE = 200;

    private final TransactionTemplate transactionTemplate;
    private final ReconExceptionService reconExceptionService;
    private final ChannelStatementService channelStatementService;
    private final ClearingAccountRepository clearingAccountRepository;
    private final ChannelStatementLineRepository channelStatementLineRepository;

    @Override
    protected ExpireWaitingChannelLinesResponse processRequest(ExpireWaitingChannelLinesRequest request) {
        int opened = 0;
        int skipped = 0;
        for (int offset = 0;; offset += ACCOUNT_PAGE_SIZE) {
            List<ClearingAccount> accounts = clearingAccountRepository.findPage(null, null, null,
                ClearingAccountStatus.ACTIVE, offset, ACCOUNT_PAGE_SIZE);
            for (ClearingAccount account : accounts) {
                Instant cutoff = request.getNow().minus(Duration.ofDays(account.getUnmatchedWaitDays()));
                for (ChannelStatementLine line : channelStatementLineRepository.listWaitExpired(account.getId(), cutoff,
                    BATCH_LIMIT)) {
                    boolean done = expire(account, line);
                    opened += done ? 1 : 0;
                    skipped += done ? 0 : 1;
                }
            }
            if (accounts.size() < ACCOUNT_PAGE_SIZE) {
                return new ExpireWaitingChannelLinesResponse(opened, skipped);
            }
        }
    }

    /** 一行一个短事务：开异常并转 EXCEPTION；行已被并发修改时回滚并返回 false。 */
    boolean expire(ClearingAccount account, ChannelStatementLine line) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                reconExceptionService.open(command(account, line));
                channelStatementService.markLineException(line);
            });
            return true;
        } catch (ConcurrentRequestException changed) {
            return false;
        }
    }

    /** CHANNEL_LINE_UNMATCHED 异常命令。 */
    static OpenReconExceptionCommand command(ClearingAccount account, ChannelStatementLine line) {
        return OpenReconExceptionCommand.builder().exceptionType(ReconExceptionType.CHANNEL_LINE_UNMATCHED)
            .sourceType(ReconExceptionSourceType.CHANNEL_STATEMENT_LINE).sourceId(line.getId())
            .statementId(line.getStatementId()).clearingAccountId(account.getId())
            .entityType(ReconEntityType.OPERATING_ENTITY).entityId(account.getEntityNo()).currency(account.getCurrency())
            .differenceAmount(ChannelStatementLines.netContribution(line))
            .detail("第 " + line.getLineNo() + " 行等待 " + account.getUnmatchedWaitDays() + " 天仍未命中平台流水").build();
    }
}
