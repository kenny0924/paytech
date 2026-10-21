package com.paytech.reconciliation.service.processor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.reconciliation.api.enums.BankStatementStatus;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.core.constant.ReconciliationLocks;
import com.paytech.reconciliation.core.dal.dataobject.BankStatement;
import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;
import com.paytech.reconciliation.domain.Amounts;
import com.paytech.reconciliation.domain.BankStatementDuplicates;
import com.paytech.reconciliation.domainservice.repository.BankStatementLineRepository;
import com.paytech.reconciliation.domainservice.repository.BankStatementRepository;
import com.paytech.reconciliation.domainservice.service.BankStatementService;
import com.paytech.reconciliation.service.processor.request.AppendBankStatementLinesRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 追加一段银行流水（UC-R11，#18，technique D-01）
 * <li>锁 bank-statement:{id}；对账单须为 RECEIVING，操作人须为开始导入的上传人。</li>
 * <li>段内行号不得重复，行号不超过声明行数；金额必须大于 0 且不超过币种精度。</li>
 * <li>按行号幂等：已接收的同号同内容忽略，同号不同内容拒绝；已记为重复的行号重发忽略。</li>
 * <li>银行交易号重复（同一银行账户已在其他对账单导入，或本文件另一行已用）的行受唯一键约束不能入库：记入重复行数，并在接收中的
 * reject_reason 记下「第 n 行：原因」（{@link BankStatementDuplicates}），完成导入时整单拒绝（2026-10-20 主会话决定，AC-09）。</li>
 */
@Component
@AllArgsConstructor
public class AppendBankStatementLinesProcessor
    extends AbstractLockProcessor<AppendBankStatementLinesRequest, BankStatement> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final BankStatementService bankStatementService;
    private final BankStatementRepository bankStatementRepository;
    private final BankStatementLineRepository bankStatementLineRepository;

    /** 银行对账单资源锁。 */
    @Override
    protected LockCommand lockCommand(AppendBankStatementLinesRequest request) {
        return LockCommand.tryLocks(ReconciliationLocks.bankStatement(request.getStatementId()), ReconciliationLocks.WAIT,
            ReconciliationLocks.LEASE);
    }

    @Override
    protected BankStatement processWithinLock(AppendBankStatementLinesRequest request) {
        // 业务验证
        BankStatement statement = loadReceiving(request);
        checkSegment(statement, request.getLines());

        // 按行号与银行交易号比较已入库的流水，只保留新行
        AppendPlan plan = plan(statement, request.getLines());

        // 本地保存
        bankStatementService.appendLines(statement, plan.getNewLines(), plan.getDuplicateCount(),
            plan.getPendingReasons());
        return statement;
    }

    /** 对账单存在、RECEIVING 且操作人为上传人。 */
    BankStatement loadReceiving(AppendBankStatementLinesRequest request) {
        BankStatement statement = bankStatementRepository.findById(request.getStatementId());
        if (statement == null) {
            throw new BizCheckFailException("银行对账单不存在: " + request.getStatementId());
        }
        if (!statement.getMakerId().equals(request.getActorId())) {
            throw new BizCheckFailException("只有开始导入的上传人可以追加流水: 对账单 " + statement.getId());
        }
        if (statement.getStatus() != BankStatementStatus.RECEIVING) {
            throw new BizCheckFailException("银行对账单不在接收中，不能追加流水: 对账单 " + statement.getId() + ", 状态 "
                + statement.getStatus().getCode());
        }
        return statement;
    }

    /** 段内行号唯一、行号不超过声明行数、金额大于 0 且不超过币种精度。 */
    static void checkSegment(BankStatement statement, List<AppendBankStatementLinesRequest.Line> lines) {
        Set<Integer> lineNos = new HashSet<>();
        for (AppendBankStatementLinesRequest.Line line : lines) {
            String label = "对账单 " + statement.getId() + " 第 " + line.getLineNo() + " 行";
            if (!lineNos.add(line.getLineNo())) {
                throw new BizCheckFailException("同一段内行号重复: " + label);
            }
            if (line.getLineNo() < 1 || line.getLineNo() > statement.getDeclaredLineCount()) {
                throw new BizCheckFailException("行号超出声明行数 " + statement.getDeclaredLineCount() + ": " + label);
            }
            if (line.getAmount().signum() <= 0) {
                throw new BizCheckFailException("流水金额必须大于 0: " + label);
            }
            Amounts.checkPrecision(statement.getCurrency(), line.getAmount(), label + " 金额");
        }
    }

    /** 逐行比较已接收行与已入库交易号，得出新行与新记下的重复行。 */
    AppendPlan plan(BankStatement statement, List<AppendBankStatementLinesRequest.Line> lines) {
        Map<Integer, BankStatementLine> byLineNo = bankStatementLineRepository.listByStatementAndLineNos(
            statement.getId(), lines.stream().map(AppendBankStatementLinesRequest.Line::getLineNo).toList()).stream()
            .collect(Collectors.toMap(BankStatementLine::getLineNo, Function.identity()));
        Map<String, BankStatementLine> byRef = new HashMap<>(bankStatementLineRepository.listByTransactionRefs(
            statement.getCashAccountId(), lines.stream().map(AppendBankStatementLinesRequest.Line::getBankTransactionRef)
                .toList()).stream().collect(Collectors.toMap(BankStatementLine::getBankTransactionRef, Function.identity())));
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        AppendPlan plan = new AppendPlan(statement.getRejectReason());
        for (AppendBankStatementLinesRequest.Line input : lines) {
            BankStatementLine candidate = build(statement, input, now);
            BankStatementLine received = byLineNo.get(input.getLineNo());
            if (received != null) {
                checkSameLine(statement, received, candidate);
            } else if (!BankStatementDuplicates.noted(plan.getPendingReasons(), input.getLineNo())) {
                classify(statement, plan, byRef, candidate);
            }
        }
        return plan;
    }

    /** 新行：交易号未被占用则入库，否则记为重复。 */
    void classify(BankStatement statement, AppendPlan plan, Map<String, BankStatementLine> byRef,
        BankStatementLine candidate) {
        BankStatementLine known = byRef.get(candidate.getBankTransactionRef());
        if (known == null) {
            plan.getNewLines().add(candidate);
            byRef.put(candidate.getBankTransactionRef(), candidate);
            return;
        }
        plan.pendingReasons = BankStatementDuplicates.append(plan.getPendingReasons(), candidate.getLineNo(),
            duplicateReason(statement, known, candidate));
        plan.duplicateCount++;
    }

    /** 重复原因：本文件另一行，或已导入的另一张对账单。 */
    String duplicateReason(BankStatement statement, BankStatementLine known, BankStatementLine candidate) {
        String ref = candidate.getBankTransactionRef();
        if (known.getStatementId().equals(statement.getId())) {
            return "银行交易号 " + ref + " 与第 " + known.getLineNo() + " 行重复";
        }
        BankStatement other = bankStatementRepository.findById(known.getStatementId());
        return "银行交易号 " + ref + " 已在对账单 " + other.getStatementRef() + " 导入，请上传不重叠的对账单";
    }

    /** 同号已接收：内容必须相同。 */
    static void checkSameLine(BankStatement statement, BankStatementLine received, BankStatementLine candidate) {
        if (!candidate.getBankTransactionRef().equals(received.getBankTransactionRef())
            || !sameMovement(received, candidate) || !Objects.equals(received.getCounterpartyName(),
                candidate.getCounterpartyName()) || !Objects.equals(received.getReferenceText(),
                candidate.getReferenceText())) {
            throw new BizCheckFailException("同一行号已接收不同内容: 对账单 " + statement.getId() + " 第 "
                + candidate.getLineNo() + " 行");
        }
    }

    /** 方向、金额（数值）、起息日相同。 */
    static boolean sameMovement(BankStatementLine left, BankStatementLine right) {
        return left.getDirection() == right.getDirection() && left.getAmount().compareTo(right.getAmount()) == 0
            && left.getValueDate().equals(right.getValueDate());
    }

    /** 由请求行构造 UNMATCHED 流水。 */
    BankStatementLine build(BankStatement statement, AppendBankStatementLinesRequest.Line input, Instant now) {
        BankStatementLine line = new BankStatementLine();
        line.setId(idGenerator.nextId());
        line.setStatementId(statement.getId());
        line.setCashAccountId(statement.getCashAccountId());
        line.setLineNo(input.getLineNo());
        line.setBankTransactionRef(input.getBankTransactionRef());
        line.setValueDate(input.getValueDate());
        line.setDirection(input.getDirection());
        line.setAmount(input.getAmount());
        line.setCounterpartyName(input.getCounterpartyName());
        line.setReferenceText(input.getReferenceText());
        line.setMatchedAmount(BigDecimal.ZERO);
        line.setMatchStatus(LineMatchStatus.UNMATCHED);
        line.setVersion(1L);
        line.setCreateTime(now);
        line.setUpdateTime(now);
        return line;
    }

    /** 一段的保存计划：新行、新记下的重复行数与累计的逐行重复原因。 */
    @Getter
    static class AppendPlan {
        /** 需要插入的新行 */
        private final List<BankStatementLine> newLines = new ArrayList<>();

        /** 本段新记下的重复交易号行数 */
        private int duplicateCount;

        /** 累计的逐行重复原因（含此前各段） */
        private String pendingReasons;

        /** 以已记下的原因开始。 */
        AppendPlan(String pendingReasons) {
            this.pendingReasons = pendingReasons;
        }
    }
}
