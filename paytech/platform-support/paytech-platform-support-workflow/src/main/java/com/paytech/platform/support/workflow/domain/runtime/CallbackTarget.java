package com.paytech.platform.support.workflow.domain.runtime;

import java.util.List;

/**
 * t_workflow_callback.target 的结构，登记后不可变。
 *
 * <p>NOTIFY 只用 recipientIds 与 channels（目前只有 EMAIL 需要投递，我的待办是查询结果）；CALLBACK 用 callbackCode，
 * 并冻结发生时的状态变化、操作人、意见与实例版本，供发布事件时原样使用（这些值不在其他表中按次保存）。</p>
 *
 * @param recipientIds NOTIFY 收件人账号；CALLBACK 为空列表
 * @param channels NOTIFY 需要投递的渠道；CALLBACK 为空列表
 * @param callbackCode CALLBACK 回调编码；NOTIFY 为 null
 * @param fromStatus 变化前状态；没有时为 null
 * @param toStatus 变化后状态；没有时为 null
 * @param actorId 触发变化的账号；系统推进时为 null
 * @param comment 审批意见、转交说明或终止原因；没有时为 null
 * @param instanceVersion 变化后的实例版本，作为事件聚合版本
 */
public record CallbackTarget(List<Long> recipientIds, List<String> channels, String callbackCode, String fromStatus,
    String toStatus, Long actorId, String comment, long instanceVersion) {

    /** 复制列表，保持不可变。 */
    public CallbackTarget {
        recipientIds = List.copyOf(recipientIds);
        channels = List.copyOf(channels);
    }
}
