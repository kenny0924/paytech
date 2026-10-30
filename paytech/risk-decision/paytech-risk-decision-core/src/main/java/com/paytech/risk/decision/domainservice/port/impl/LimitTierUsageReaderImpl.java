package com.paytech.risk.decision.domainservice.port.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.risk.control.domain.limittier.CurrencyUsage;
import com.paytech.risk.control.domain.limittier.TierQuotaVariable;
import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.control.domainservice.port.LimitTierUsageReader;
import com.paytech.risk.decision.core.dal.dataobject.RiskVariableDefinition;
import com.paytech.risk.decision.core.dal.dataobject.RiskVariableMember;
import com.paytech.risk.decision.domain.enums.NumericUnit;
import com.paytech.risk.decision.domain.enums.VariableSource;
import com.paytech.risk.decision.domain.enums.VelocityEventCode;
import com.paytech.risk.decision.domainservice.repository.ClientAllowedDailyTotalRepository;
import com.paytech.risk.decision.domainservice.repository.RiskVariableMemberRepository;
import com.paytech.risk.decision.domainservice.repository.RiskVariableRepository;
import com.paytech.risk.decision.domainservice.repository.VelocityEventSubjectRepository;

import lombok.AllArgsConstructor;

/**
 * 档位端口的决策模块实现：读取 PAYMENT_ATTEMPT_ALLOWED 累计事件（CLIENT 主体）、Client 日放行汇总与 TIER_ 前缀风控常量
 * <li>与评估时累计变量走同一索引 idx_velocity_subject；只读本库，无外部调用。</li>
 */
@Component
@AllArgsConstructor
public class LimitTierUsageReaderImpl implements LimitTierUsageReader {
    /** 档位额度常量与集合的编码前缀。 */
    static final String TIER_PREFIX = "TIER_";

    /** 近 30 日已用包含的 UTC 自然日数（含当日）。 */
    static final int LAST_30_DAYS = 30;

    /** 单个币种集合最多读取的成员数（ISO 4217 币种不超过 300 个）。 */
    static final int MEMBER_LIMIT = 500;

    private final RiskVariableRepository variables;
    private final RiskVariableMemberRepository members;
    private final VelocityEventSubjectRepository subjects;
    private final ClientAllowedDailyTotalRepository dailyTotals;

    /** 观察期放行笔数：CLIENT 主体的放行支付尝试事件数。 */
    @Override
    public long countAllowedPaymentAttempts(long clientId, Instant from, Instant to) {
        return subjects.countEvents(RiskSubjectType.CLIENT, Long.toString(clientId), VelocityEventCode.PAYMENT_ATTEMPT_ALLOWED,
            from, to);
    }

    /** 已用额度：按币种读日放行汇总的当日与近 30 日合计。 */
    @Override
    public List<CurrencyUsage> allowedPaymentAmounts(long clientId, Instant at) {
        LocalDate today = LocalDate.ofInstant(at, ZoneOffset.UTC);
        return dailyTotals.totalsByCurrency(clientId, VelocityEventCode.PAYMENT_ATTEMPT_ALLOWED, today,
            today.minusDays(LAST_30_DAYS - 1L)).stream()
            .map(total -> new CurrencyUsage(total.getCurrency(), total.getAmountToday(), total.getAmountLast30Days()))
            .toList();
    }

    /** 常量取数值、单位与固定币种；集合按排序读取成员值。 */
    @Override
    public List<TierQuotaVariable> tierQuotaVariables() {
        return variables.activeConstantsByCodePrefix(TIER_PREFIX).stream().map(this::quotaVariable).toList();
    }

    /** 变量根转换为端口原值。 */
    TierQuotaVariable quotaVariable(RiskVariableDefinition root) {
        if (root.sourceType() == VariableSource.COLLECTION) {
            List<String> values = members.members(root.variableCode(), null, MEMBER_LIMIT, 0, false).stream()
                .map(LimitTierUsageReaderImpl::memberValue).toList();
            return new TierQuotaVariable(root.variableCode(), true, null, false, null, values);
        }
        return new TierQuotaVariable(root.variableCode(), false, root.numberValue(),
            root.numericUnit() == NumericUnit.MONEY, root.currencyCode(), List.of());
    }

    /** 集合成员的字符串值，缺失时取成员键。 */
    static String memberValue(RiskVariableMember member) {
        return member.stringValue() != null ? member.stringValue() : member.valueKey();
    }
}
