package com.paytech.finance.accounting.domain;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import com.paytech.finance.accounting.core.dal.dataobject.AccountingRuleBundleVer;
import com.paytech.finance.accounting.core.dal.dataobject.AccountingRuleLine;
import com.paytech.finance.accounting.core.dal.dataobject.ChannelAccount;
import com.paytech.finance.accounting.core.dal.dataobject.TransactionEconomicComponent;
import com.paytech.finance.accounting.core.dal.dataobject.TransactionEconomics;
import com.paytech.finance.accounting.core.enums.AmountSource;
import com.paytech.finance.accounting.core.enums.AutoInstructionType;

import lombok.RequiredArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** 一次交易自动记账的已校验输入：经济结果、组件、生效规则版本与规则行；渠道清算账户按需解析后回填。 */
@Getter
@RequiredArgsConstructor
public class TransactionPostingContext {
    /** 调用方命令。 */
    private final TransactionPostingCommand command;

    /** 已校验的 PAYMENT 交易经济结果。 */
    private final TransactionEconomics economics;

    /** 经济结果全部组件，按组件序号升序。 */
    private final List<TransactionEconomicComponent> components;

    /** 支付成功时点生效的规则版本。 */
    private final AccountingRuleBundleVer bundleVer;

    /** 生效规则版本的全部规则行。 */
    private final List<AccountingRuleLine> ruleLines;

    /** 首次被规则行引用时解析的渠道清算账户。 */
    @Setter
    private ChannelAccount channelAccount;

    /** 交易经济结果主键。 */
    public long economicsId() {
        return economics.getId();
    }

    /** 某指令类型的规则行，按行号升序。 */
    public List<AccountingRuleLine> rulesOf(AutoInstructionType type) {
        return ruleLines.stream().filter(line -> line.getInstructionType() == type)
            .sorted(Comparator.comparing(AccountingRuleLine::getLineNo)).toList();
    }

    /** 某组件类型的全部组件。 */
    public List<TransactionEconomicComponent> componentsOf(String componentType) {
        return components.stream().filter(component -> componentType.equals(component.getComponentType())).toList();
    }

    /**
     * 金额来源的取值
     *
     * @return GROSS_AMOUNT 取 Header 总额；其余为同名组件 posted_amount 之和，不存在该类组件时为空
     */
    public BigDecimal amountOf(AmountSource source) {
        if (source == AmountSource.GROSS_AMOUNT) {
            return economics.getGrossAmount();
        }
        List<TransactionEconomicComponent> matched = componentsOf(source.getCode());
        if (matched.isEmpty()) {
            return null;
        }
        return matched.stream().map(TransactionEconomicComponent::getPostedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
