package com.paytech.reconciliation.domain;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import com.paytech.reconciliation.api.enums.ExpectedMovementStatus;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;

/**
 * 银行层匹配的纯规则（设计 F2、§6.4 不变量 5–6）
 * <li>匹配金额存正数；对流水的带符号贡献由预期流水方向与流水方向是否相同决定（同向为正）。</li>
 * <li>预期流水剩余 = 预期 − 已确认 − 已归类（归类不足为正、超出为负）；流水剩余 = 金额 − 已匹配。</li>
 */
public final class BankMatching {
    private BankMatching() {
    }

    /** 一条分配对流水的带符号贡献。 */
    public static BigDecimal signedContribution(BankStatementLine line, ExpectedBankMovement movement,
        BigDecimal amount) {
        return line.getDirection() == movement.getDirection() ? amount : amount.negate();
    }

    /** 预期流水尚未确认或归类的剩余金额。 */
    public static BigDecimal movementRemaining(ExpectedBankMovement movement) {
        return movement.getExpectedAmount().subtract(movement.getConfirmedAmount())
            .subtract(movement.getClassifiedAmount());
    }

    /** 流水尚未匹配的金额（金额 − 已匹配带符号合计）。 */
    public static BigDecimal lineRemaining(BankStatementLine line) {
        return line.getAmount().subtract(line.getMatchedAmount());
    }

    /** 流水未匹配金额按流水方向带符号（贷方为正、借方为负），即人工开 BANK_LINE_UNMATCHED 的差异金额。 */
    public static BigDecimal lineSignedRemaining(BankStatementLine line) {
        BigDecimal remaining = lineRemaining(line);
        return line.getDirection() == SettlementDirection.CREDIT ? remaining : remaining.negate();
    }

    /** 由已匹配合计推出流水匹配状态。 */
    public static LineMatchStatus lineStatus(BankStatementLine line, BigDecimal matchedAmount) {
        if (matchedAmount.signum() == 0) {
            return LineMatchStatus.UNMATCHED;
        }
        return matchedAmount.compareTo(line.getAmount()) == 0 ? LineMatchStatus.MATCHED
            : LineMatchStatus.PARTIALLY_MATCHED;
    }

    /** 由已确认与已归类金额推出预期流水状态：两者合计满额为 CONFIRMED。 */
    public static ExpectedMovementStatus movementStatus(ExpectedBankMovement movement, BigDecimal confirmedAmount,
        BigDecimal classifiedAmount) {
        if (confirmedAmount.add(classifiedAmount).compareTo(movement.getExpectedAmount()) == 0) {
            return ExpectedMovementStatus.CONFIRMED;
        }
        return confirmedAmount.signum() == 0 ? ExpectedMovementStatus.OPEN : ExpectedMovementStatus.PARTIALLY_CONFIRMED;
    }

    /**
     * 两个日期相差的工作日数（只排除周六、周日，切片 1 不接节假日日历）
     *
     * @param from 起点
     * @param to 终点
     * @return 非负工作日数
     */
    public static int businessDaysBetween(LocalDate from, LocalDate to) {
        LocalDate start = from.isBefore(to) ? from : to;
        LocalDate end = from.isBefore(to) ? to : from;
        int days = 0;
        for (LocalDate day = start.plusDays(1); !day.isAfter(end); day = day.plusDays(1)) {
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                days++;
            }
        }
        return days;
    }
}
