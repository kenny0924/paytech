package com.paytech.risk.decision.service.query;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.risk.control.service.RiskListMembershipService;
import com.paytech.risk.decision.core.dal.dataobject.ListFeatureDefinition;
import com.paytech.risk.decision.core.dal.dataobject.VelocityEvent;
import com.paytech.risk.decision.core.dal.dataobject.VelocityFeatureDefinition;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.runtime.ExecutionSnapshot;
import com.paytech.risk.decision.domain.runtime.FeatureValue;
import com.paytech.risk.decision.domain.runtime.PaymentRiskInput;
import com.paytech.risk.decision.domainservice.repository.VelocityEventSubjectRepository;

import lombok.AllArgsConstructor;

/**
 * 计算执行发布引用的全部 Feature
 * <li>实时累计窗口为 [评估时间 − 窗口, 评估时间)，以评估时间为锚点，回放时按原评估时间计算。</li>
 * <li>本次没有锚定主体时值为空，不算失败；数据库异常向上抛出，由调用方记为技术失败。</li>
 */
@Component
@AllArgsConstructor
public class FeatureValueReader {
    private final RiskListMembershipService lists;
    private final VelocityEventSubjectRepository subjects;

    /**
     * 计算 Feature
     *
     * @param snapshot
     *            执行内容
     * @param input
     *            冻结输入
     * @param at
     *            评估时间
     * @return 按 Feature Version 索引的结果，执行顺序先累计后名单，各自按 ID 升序
     */
    public Map<Long, FeatureValue> read(ExecutionSnapshot snapshot, PaymentRiskInput input, Instant at) {
        Map<Long, FeatureValue> result = new HashMap<>();
        List<Long> velocityIds = new ArrayList<>(snapshot.velocityFeatures().keySet());
        velocityIds.sort(null);
        for (Long id : velocityIds) {
            long started = System.nanoTime();
            Object value = velocity(snapshot.velocityFeatures().get(id), input, at);
            result.put(id, new FeatureValue(id, result.size() + 1, DecisionValueType.NUMBER, value, elapsed(started)));
        }
        List<Long> listIds = new ArrayList<>(snapshot.listFeatures().keySet());
        listIds.sort(null);
        for (Long id : listIds) {
            long started = System.nanoTime();
            Object value = membership(snapshot.listFeatures().get(id), input, at);
            result.put(id, new FeatureValue(id, result.size() + 1, DecisionValueType.BOOLEAN, value, elapsed(started)));
        }
        return result;
    }

    /** 实时累计；数值统一为去尾零的十进制文本。 */
    Object velocity(VelocityFeatureDefinition definition, PaymentRiskInput input, Instant at) {
        String key = input.subjectKey(definition.getAnchorSubjectType());
        if (key == null) {
            return null;
        }
        Instant from = at.minusSeconds(definition.getWindowSeconds());
        BigDecimal value = switch (definition.getMetric()) {
            case COUNT -> BigDecimal.valueOf(
                subjects.countEvents(definition.getAnchorSubjectType(), key, definition.getEventCode(), from, at)
                    + (definition.isIncludeCurrent() ? 1 : 0));
            case SUM_AMOUNT -> subjects.sumAmount(definition.getAnchorSubjectType(), key, definition.getEventCode(),
                from, at, input.currency()).add(definition.isIncludeCurrent() ? input.amount() : BigDecimal.ZERO);
            case DISTINCT_COUNT -> BigDecimal.valueOf(subjects.countDistinct(definition.getAnchorSubjectType(), key,
                definition.getEventCode(), from, at, definition.getDistinctSubjectType()));
            case LAST_AMOUNT ->
                last(definition, key, from, at).filter(event -> input.currency().equals(event.getCurrency()))
                    .map(VelocityEvent::getAmount).orElse(null);
            case SECONDS_SINCE_LAST -> last(definition, key, from, at)
                .map(event -> BigDecimal.valueOf(Duration.between(event.getOccurredTime(), at).toSeconds()))
                .orElse(null);
        };
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    /** 窗口内最近一次事件。 */
    java.util.Optional<VelocityEvent> last(VelocityFeatureDefinition definition, String key, Instant from, Instant at) {
        return subjects.findLast(definition.getAnchorSubjectType(), key, definition.getEventCode(), from, at);
    }

    /** 名单命中；本次没有该主体时为 false。 */
    Object membership(ListFeatureDefinition definition, PaymentRiskInput input, Instant at) {
        String key = input.subjectKey(definition.getSubjectType());
        return key != null && lists.isMember(definition.getListId(), definition.getSubjectType(), key, at);
    }

    /** 毫秒耗时。 */
    static int elapsed(long startedNanos) {
        return (int)Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }
}
