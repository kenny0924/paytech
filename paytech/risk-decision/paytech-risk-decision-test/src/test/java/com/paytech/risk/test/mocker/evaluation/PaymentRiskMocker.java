package com.paytech.risk.test.mocker.evaluation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.risk.test.mapper.evaluation.PaymentRiskTestMapper;

/**
 * 支付风控的本地数据 Mocker
 * <li>插入历史累计事件，构造窗口与算法场景；Clear 清理评估、累计、名单、Feature、影子批次与支付决策点下测试创建的配置。</li>
 * <li>可把决策点版本超时改为 -1 强制每次评估超时；Clear 先恢复原超时。</li>
 * <li>经业务入口创建的数据同样由 Clear 统一回收，用例无需逐项登记。</li>
 *
 * @author Kenny
 */
@TestComponent
public class PaymentRiskMocker implements Clear {
    private final PaymentRiskTestMapper mapper;

    /** 被改写前的决策点超时；为空表示未改写。 */
    private Integer originalTimeoutMillis;

    public PaymentRiskMocker(PaymentRiskTestMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 插入一条历史累计事件
     *
     * @param eventCode 事件编码
     * @param occurred 事件时间
     * @param amount 金额
     * @param currency 币种
     * @param subjects 主体类型到规范化主体键
     * @return 事件 ID
     */
    public long mockVelocityEvent(String eventCode, Instant occurred, String amount, String currency,
        Map<String, String> subjects) {
        long id = randomId();
        mapper.insertVelocityEvent(id, eventCode, occurred, new BigDecimal(amount), currency, subjects, randomId());
        return id;
    }

    /** 支付决策点版本超时改为 -1：任何耗时都超过上限，评估确定记为 TIMED_OUT。 */
    public void mockEvaluationTimeout() {
        if (originalTimeoutMillis == null) {
            originalTimeoutMillis = mapper.pointTimeoutMillis();
        }
        mapper.updatePointTimeoutMillis(-1);
    }

    /** 删除评估的流程轨迹，使其等同本功能上线前写入的评估。 */
    public void mockEvaluationWithoutPath(long evaluationId) {
        mapper.deleteFlowTraces(evaluationId);
    }

    /** 名单状态改为未登记的值：引用该名单的名单命中 Feature 在计算时读取失败。 */
    public void mockListCorrupted(String listCode) {
        mapper.updateListStatus(listCode, "CORRUPTED");
    }

    /** 评估之后改写数值常量的当前值。 */
    public void mockConstantChanged(String variableCode, String value) {
        mapper.updateConstantNumber(variableCode, new BigDecimal(value));
    }

    /** 恢复决策点超时并删除本测试环境的支付风控数据。 */
    @Override
    public void clear() {
        if (originalTimeoutMillis != null) {
            mapper.updatePointTimeoutMillis(originalTimeoutMillis);
            originalTimeoutMillis = null;
        }
        mapper.deleteAll();
    }

    /** 15 位正整数 ID，远离 Snowflake 生成的生产 ID 区间。 */
    private static long randomId() {
        return ThreadLocalRandom.current().nextLong(100_000_000_000_000L, 900_000_000_000_000L);
    }
}
