package com.paytech.foundation.messaging.core.observability;

/**
 * 一次消息投递的处理结果（design/08 §4）。
 *
 * @author Claude
 */
public enum ConsumeOutcome {

    /** Handler 已在本地事务内处理并提交。 */
    HANDLED("handled"),

    /** 事件 ID 已消费过，确认并跳过。 */
    REPLAYED("replayed"),

    /** 聚合版本不大于已处理版本，确认并跳过。 */
    STALE("stale"),

    /** 严格有序 Handler 遇到版本缺口，拒绝并重新投递。 */
    GAP("gap"),

    /** 非法信封、超过投递上限或不可处理，进入 DLQ。 */
    DEAD("dead");

    private final String tag;

    ConsumeOutcome(String tag) {
        this.tag = tag;
    }

    /**
     * 指标标签值。
     *
     * @return 小写标签
     */
    public String tag() {
        return tag;
    }
}
