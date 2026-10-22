package com.paytech.channel.connector.protocol.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渠道、账号和 MID 的静态生命周期。 */
@Getter
@AllArgsConstructor
public enum ChannelStatus implements CodeEnum {
    /** DRAFT 对应的稳定业务代码。 */
    DRAFT("DRAFT"),

    /** ACTIVE 对应的稳定业务代码。 */
    ACTIVE("ACTIVE"),

    /** SUSPENDED 对应的稳定业务代码。 */
    SUSPENDED("SUSPENDED"),

    /** CLOSED 对应的稳定业务代码。 */
    CLOSED("CLOSED");

    /** 数据库存储及业务比较使用的稳定代码。 */
    private final String code;

    /**
     * 渠道、账号与 MID 共用的人工状态迁移规则
     * <li>DRAFT→ACTIVE、ACTIVE⇄SUSPENDED、DRAFT|ACTIVE|SUSPENDED→CLOSED；CLOSED 为终态，同状态不算迁移。</li>
     *
     * @param target 目标状态
     * @return 是否允许从当前状态迁移到目标状态
     */
    public boolean canTransitTo(ChannelStatus target) {
        return switch (this) {
            case DRAFT -> target == ACTIVE || target == CLOSED;
            case ACTIVE -> target == SUSPENDED || target == CLOSED;
            case SUSPENDED -> target == ACTIVE || target == CLOSED;
            case CLOSED -> false;
        };
    }
}
