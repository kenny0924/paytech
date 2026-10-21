package com.paytech.reconciliation.service.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.reconciliation.api.enums.CashDirection;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.core.constant.ReconciliationLocks;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.domain.ChannelStatementLines;
import com.paytech.reconciliation.domain.ChannelStatementTotals;
import com.paytech.reconciliation.domain.LineRejection;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementLineRepository;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementRepository;
import com.paytech.reconciliation.domainservice.service.ChannelStatementService;
import com.paytech.reconciliation.service.processor.request.ChannelStatementCommandRequest;
import com.paytech.reconciliation.service.processor.response.CompleteChannelStatementResponse;

import lombok.AllArgsConstructor;

/**
 * 完成渠道账单导入（UC-R06，#14，technique D-01、D-10）：一次性校验，通过进入 MATCHING 并登记匹配任务，不通过整单 REJECTED
 * <li>锁 channel-statement:{id}；操作人须为上传人。已是 MATCHING / REJECTED / 已对平时返回当前状态（逐行原因只在首次完成时返回）；已作废拒绝。</li>
 * <li>校验：已接收行数等于声明行数（行号 1..N 连续）；交易行（PAYMENT / REFUND / CHARGEBACK）必须带外部交易号，交易号类型与交易号同有同无；
 * PAYMENT 为 CREDIT、REFUND / CHARGEBACK 为 DEBIT；账单头恒等式；切片 1 净额为借方（DEBIT）的账单整单拒绝（决定 I-04）。</li>
 * <li>匹配在提交后的本地任务 CHANNEL_STATEMENT_MATCH 中执行，本事务只做校验与状态推进。</li>
 */
@Component
@AllArgsConstructor
public class CompleteChannelStatementImportProcessor
    extends AbstractLockProcessor<ChannelStatementCommandRequest, CompleteChannelStatementResponse> {
    /** 汇总原因列上限。 */
    static final int REASON_LIMIT = 1000;

    private final ChannelStatementService channelStatementService;
    private final ChannelStatementRepository channelStatementRepository;
    private final ChannelStatementLineRepository channelStatementLineRepository;

    /** 渠道账单资源锁。 */
    @Override
    protected LockCommand lockCommand(ChannelStatementCommandRequest request) {
        return LockCommand.tryLocks(ReconciliationLocks.channelStatement(request.getStatementId()),
            ReconciliationLocks.WAIT, ReconciliationLocks.LEASE);
    }

    @Override
    protected CompleteChannelStatementResponse processWithinLock(ChannelStatementCommandRequest request) {
        // 查询原账单
        ChannelStatement statement = load(request);
        if (statement.getStatus() != ChannelStatementStatus.RECEIVING) {
            return new CompleteChannelStatementResponse(statement, List.of());
        }

        // 一次性校验
        List<LineRejection> rejections = validate(statement,
            channelStatementLineRepository.listByStatement(statement.getId()));

        // 本地保存
        if (rejections.isEmpty()) {
            channelStatementService.accept(statement);
        } else {
            channelStatementService.reject(statement, summary(rejections));
        }
        return new CompleteChannelStatementResponse(statement, rejections);
    }

    /** 账单存在、未作废、操作人为上传人。 */
    ChannelStatement load(ChannelStatementCommandRequest request) {
        ChannelStatement statement = channelStatementRepository.findById(request.getStatementId());
        if (statement == null) {
            throw new BizCheckFailException("渠道账单不存在: " + request.getStatementId());
        }
        if (!statement.getMakerId().equals(request.getActorId())) {
            throw new BizCheckFailException("只有开始导入的上传人可以完成导入: 账单 " + statement.getId());
        }
        if (statement.getStatus() == ChannelStatementStatus.VOIDED) {
            throw new BizCheckFailException("渠道账单已作废，不能完成导入: 账单 " + statement.getId());
        }
        return statement;
    }

    /** 行数、逐行规则、恒等式与净额方向；返回全部拒绝原因（整单原因在前）。 */
    static List<LineRejection> validate(ChannelStatement statement, List<ChannelStatementLine> lines) {
        List<LineRejection> rejections = new ArrayList<>();
        if (lines.size() != statement.getDeclaredLineCount()) {
            rejections.add(new LineRejection(null, "已接收 " + lines.size() + " 行，与声明行数 "
                + statement.getDeclaredLineCount() + " 不一致"));
        }
        if (statement.getCashDirection() == CashDirection.DEBIT) {
            rejections.add(new LineRejection(null, "净额为借方（DEBIT）的渠道账单本期不支持"));
        }
        ChannelStatementTotals.mismatches(statement, lines).forEach(reason -> rejections.add(new LineRejection(null,
            reason)));
        for (ChannelStatementLine line : lines) {
            String reason = lineReason(line);
            if (reason != null) {
                rejections.add(new LineRejection(line.getLineNo(), reason));
            }
        }
        return rejections;
    }

    /** 单行的组合规则；合法返回 null。 */
    static String lineReason(ChannelStatementLine line) {
        boolean hasType = line.getReferenceType() != null;
        boolean hasId = line.getExternalTransactionId() != null;
        if (hasType != hasId) {
            return "外部交易号类型与交易号必须同时填写或同时为空";
        }
        if (!ChannelStatementLines.isTransactionLine(line.getLineType())) {
            return null;
        }
        if (!hasType) {
            return line.getLineType().getCode() + " 行必须带外部交易号";
        }
        if (line.getDirection() != ChannelStatementLines.requiredDirection(line.getLineType())) {
            return line.getLineType().getCode() + " 行方向必须为 "
                + ChannelStatementLines.requiredDirection(line.getLineType()).getCode();
        }
        return null;
    }

    /** 汇总原因：条数加前几条原因，截断到列上限。 */
    static String summary(List<LineRejection> rejections) {
        String detail = rejections.stream().map(rejection -> rejection.getLineNo() == null ? rejection.getReason()
            : "第 " + rejection.getLineNo() + " 行：" + rejection.getReason()).collect(Collectors.joining("；"));
        String text = "校验不通过，共 " + rejections.size() + " 条原因：" + detail;
        return text.length() <= REASON_LIMIT ? text : text.substring(0, REASON_LIMIT);
    }
}
