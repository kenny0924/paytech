package com.paytech.channel.connector.domainservice.validator.channel;

import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.foundation.common.error.BizCheckFailException;

/** 渠道、技术账号与 MID 状态变更共用的迁移校验；只读入参，不访问数据库。 */
public final class ChannelStatusTransitionValidator {

    /** 工具类不实例化。 */
    private ChannelStatusTransitionValidator() {
    }

    /**
     * 拒绝非法状态迁移，提示包含对象、当前状态与目标状态
     *
     * @param objectLabel 对象中文名与 ID，例如「渠道 123」
     * @param current 当前状态
     * @param target 目标状态
     */
    public static void checkTransition(String objectLabel, ChannelStatus current, ChannelStatus target) {
        if (!current.canTransitTo(target)) {
            throw new BizCheckFailException("状态不允许变更: " + objectLabel + ", 当前状态 " + current.getCode()
                + ", 目标状态 " + target.getCode());
        }
    }
}
