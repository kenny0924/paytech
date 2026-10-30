package com.paytech.risk.decision.service.query;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.paytech.risk.control.service.RiskListMembershipService;
import com.paytech.risk.decision.core.dal.dataobject.ListFeatureDefinition;
import com.paytech.risk.decision.core.dal.dataobject.VelocityEvent;
import com.paytech.risk.decision.core.dal.dataobject.VelocityFeatureDefinition;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.runtime.ExecutionSnapshot;
import com.paytech.risk.decision.domain.runtime.FeatureComputationException;
import com.paytech.risk.decision.domain.runtime.FeatureValue;
import com.paytech.risk.decision.domain.runtime.RuntimeRiskInput;
import com.paytech.risk.decision.domainservice.repository.ClientAllowedDailyTotalRepository;
import com.paytech.risk.decision.domainservice.repository.VelocityEventSubjectRepository;

import lombok.AllArgsConstructor;

/**
 * 计算执行发布引用的全部 Feature
 * <li>实时累计窗口为 [评估时间 − 窗口, 评估时间)，以评估时间为锚点，回放时按原评估时间计算。</li>
 * <li>本次没有锚定主体时值为空，不算失败；计算异常（如数据库异常）停止计算并带上已完成结果抛出，由调用方记为技术失败。</li>
 */
@Component
@AllArgsConstructor
public class FeatureValueReader {
    /** 按日汇总的"当日"窗口秒数。 */
    static final int DAILY_WINDOW_SECONDS = 86_400;

    /** 按日汇总的"近 30 日"窗口包含的 UTC 自然日数（含当日）。 */
    static final int MONTH_WINDOW_DAYS = 30;

    private final RiskListMembershipService lists;
    private final VelocityEventSubjectRepository subjects;
    private final ClientAllowedDailyTotalRepository dailyTotals;

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
     *         <li>某个 Feature 计算出错时停止，抛出 {@link FeatureComputationException}，携带已完成结果与失败项。</li>
     */
    public Map<Long, FeatureValue> read(ExecutionSnapshot snapshot, RuntimeRiskInput input, Instant at) {
        Map<Long, FeatureValue> result = new HashMap<>();
        List<Long> velocityIds = new ArrayList<>(snapshot.velocityFeatures().keySet());
        velocityIds.sort(null);
        for (Long id : velocityIds) {
            compute(result, id, DecisionValueType.NUMBER, () -> velocity(snapshot.velocityFeatures().get(id), input, at));
        }
        List<Long> listIds = new ArrayList<>(snapshot.listFeatures().keySet());
        listIds.sort(null);
        for (Long id : listIds) {
            compute(result, id, DecisionValueType.BOOLEAN, () -> membership(snapshot.listFeatures().get(id), input, at));
        }
        return result;
    }

    /** 计算一个 Feature 并按执行顺序登记；失败时登记失败项后携带全部结果抛出。 */
    static void compute(Map<Long, FeatureValue> result, long id, DecisionValueType type, Supplier<Object> calculation) {
        long started = System.nanoTime();
        int sequence = result.size() + 1;
        try {
            result.put(id, new FeatureValue(id, sequence, type, calculation.get(), elapsed(started)));
        } catch (RuntimeException failure) {
            result.put(id, new FeatureValue(id, sequence, type, null, elapsed(started),
                FeatureComputationException.FEATURE_ERROR));
            throw new FeatureComputationException(result, failure);
        }
    }

    /** 实时累计；数值统一为去尾零的十进制文本。 */
    Object velocity(VelocityFeatureDefinition definition, RuntimeRiskInput input, Instant at) {
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
            case DAILY_SUM_AMOUNT -> dailySum(definition, key, input, at);
        };
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    /**
     * 按 UTC 日汇总的同币种放行金额：窗口 86400 为 UTC 当日，2592000 为近 30 个 UTC 自然日（含当日）；计入本次时加本次金额
     * <li>读取日汇总表唯一索引范围内至多 30 行，与 Client 交易量无关。</li>
     */
    BigDecimal dailySum(VelocityFeatureDefinition definition, String key, RuntimeRiskInput input, Instant at) {
        LocalDate today = LocalDate.ofInstant(at, ZoneOffset.UTC);
        LocalDate from = definition.getWindowSeconds() == DAILY_WINDOW_SECONDS ? today
            : today.minusDays(MONTH_WINDOW_DAYS - 1L);
        BigDecimal sum = dailyTotals.sumAmount(Long.parseLong(key), definition.getEventCode(), input.currency(), from, today);
        return definition.isIncludeCurrent() ? sum.add(input.amount()) : sum;
    }

    /** 窗口内最近一次事件。 */
    java.util.Optional<VelocityEvent> last(VelocityFeatureDefinition definition, String key, Instant from, Instant at) {
        return subjects.findLast(definition.getAnchorSubjectType(), key, definition.getEventCode(), from, at);
    }

    /** 名单命中；本次没有该主体时为 false。 */
    Object membership(ListFeatureDefinition definition, RuntimeRiskInput input, Instant at) {
        String key = input.subjectKey(definition.getSubjectType());
        return key != null && lists.isMember(definition.getListId(), definition.getSubjectType(), key, at);
    }

    /** 毫秒耗时。 */
    static int elapsed(long startedNanos) {
        return (int)Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }
}
