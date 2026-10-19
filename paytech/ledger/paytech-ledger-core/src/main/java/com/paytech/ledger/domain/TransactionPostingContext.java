package com.paytech.ledger.domain;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import com.paytech.billing.api.enums.AmountSource;
import com.paytech.billing.api.enums.AutoInstructionType;
import com.paytech.billing.api.fact.EconomicFactLinePayload;
import com.paytech.billing.api.fact.EconomicFactPayload;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundleVer;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleLine;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 一次控制科目过账的规则上下文：入账批次、已断言完整的经济事实、生效规则版本与规则行。 */
@Getter
@RequiredArgsConstructor
public class TransactionPostingContext {
    /** 调用方命令（入账批次与经济事实）。 */
    private final AccountPostingCommand command;

    /** 已断言完整的经济事实。 */
    private final EconomicFactPayload fact;

    /** 经济事实全部明细行，按行号升序。 */
    private final List<EconomicFactLinePayload> lines;

    /** 事实业务时点生效的规则版本。 */
    private final AccountingRuleBundleVer bundleVer;

    /** 生效规则版本的全部规则行。 */
    private final List<AccountingRuleLine> ruleLines;

    /** 经济事实主键。 */
    public long factId() {
        return fact.getFactId();
    }

    /** 某指令类型的规则行，按行号升序。 */
    public List<AccountingRuleLine> rulesOf(AutoInstructionType type) {
        return ruleLines.stream().filter(line -> line.getInstructionType() == type)
            .sorted(Comparator.comparing(AccountingRuleLine::getLineNo)).toList();
    }

    /** 某组件类型的全部明细行。 */
    public List<EconomicFactLinePayload> linesOf(String componentType) {
        return lines.stream().filter(line -> componentType.equals(line.getComponentType())).toList();
    }

    /**
     * 金额来源的取值
     *
     * @return GROSS_AMOUNT 取事实头总额；其余为同名明细行 posted_amount 之和，不存在该类明细行时为空
     */
    public BigDecimal amountOf(AmountSource source) {
        if (source == AmountSource.GROSS_AMOUNT) {
            return fact.getGrossAmount();
        }
        List<EconomicFactLinePayload> matched = linesOf(source.getCode());
        if (matched.isEmpty()) {
            return null;
        }
        return matched.stream().map(EconomicFactLinePayload::getPostedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
