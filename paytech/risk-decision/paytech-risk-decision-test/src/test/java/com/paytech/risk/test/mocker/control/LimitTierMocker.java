package com.paytech.risk.test.mocker.control;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.risk.control.core.enums.LimitTier;
import com.paytech.risk.test.mapper.control.LimitTierTestMapper;
import com.paytech.risk.test.mapper.control.LimitTierTestMapper.TierFixture;

/**
 * 插入限额档位前置数据：档位行、建档记录与 TIER 名单成员（来源记为迁移）
 * <li>只构造前置事实，不调用被测接口；数据由公共数据清理器在每例结束删除。</li>
 */
@TestComponent
public class LimitTierMocker {
    /** 前置数据 ID 起点，每个档位占用三个 ID。 */
    private static final AtomicLong IDS = new AtomicLong(8_100_000_000L);

    private final LimitTierTestMapper store;

    public LimitTierMocker(LimitTierTestMapper store) {
        this.store = store;
    }

    /** Client 处于给定档位（非观察期），名称与编号按 Client ID 生成。 */
    public void mockTier(long clientId, LimitTier tier) {
        insert(clientId, tier, null, null, null, null, "前置档位");
    }

    /** Client 处于 RESTRICTED，受限前为给定档位。 */
    public void mockRestricted(long clientId, LimitTier previousTier) {
        insert(clientId, LimitTier.RESTRICTED, previousTier, null, null, null, "前置受限");
    }

    /**
     * Client 处于观察期
     *
     * @param clientId Client ID
     * @param daysAgo 观察期开始距今天数
     * @param targetDays 目标天数
     * @param targetTransactions 目标放行笔数
     */
    public void mockObservation(long clientId, int daysAgo, int targetDays, int targetTransactions) {
        Instant started = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(daysAgo, ChronoUnit.DAYS);
        insert(clientId, LimitTier.OBSERVATION, null, started, targetDays, targetTransactions, "前置观察");
    }

    /** Client 的承保在 REQ-04 上线前已批准、没有档位：移除承保批准时建的档位数据。 */
    public void mockApprovedBeforeTiers(long clientId) {
        store.deleteTierOf(clientId);
    }

    /** 插入一组档位前置数据。 */
    private void insert(long clientId, LimitTier tier, LimitTier previousTier, Instant observationStarted,
        Integer targetDays, Integer targetTransactions, String reason) {
        long changeId = IDS.getAndAdd(3);
        Instant effective = observationStarted != null ? observationStarted : Instant.now().truncatedTo(ChronoUnit.MICROS);
        store.insertTier(new TierFixture(clientId, changeId, tier.getCode(),
            previousTier == null ? null : previousTier.getCode(), "Tier Client " + clientId, "CL-T-" + clientId, reason,
            effective, observationStarted, targetDays, targetTransactions));
    }
}
