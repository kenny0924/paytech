package com.paytech.platform.support.workflow.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 通知与业务回调的触发事件，与 t_workflow_callback.trigger_code 一致。
 *
 * <p>节点级规则配置在 TASK 节点的 notifications，实例级规则配置在流程设置 settings.notifications。</p>
 */
@Getter
@RequiredArgsConstructor
public enum NotificationTrigger implements CodeEnum {

    /** 节点激活。 */
    ACTIVATED("ACTIVATED", false),

    /** 节点进入待审批。 */
    APPROVAL_OPENED("APPROVAL_OPENED", false),

    /** 节点通过。 */
    PASSED("PASSED", false),

    /** 节点被退回修改。 */
    RETURNED_FOR_REVISION("RETURNED_FOR_REVISION", false),

    /** 节点被驳回。 */
    REJECTED("REJECTED", false),

    /**
     * 节点动作验证未通过。
     * <li>2026-10-22 去掉动作验证后不再产生；保留以兼容已发布配置与历史回调，草稿校验拒绝新配置使用。</li>
     */
    VERIFICATION_FAILED("VERIFICATION_FAILED", false),

    /** 节点动作或审批被转交。 */
    TRANSFERRED("TRANSFERRED", false),

    /** 实例发起。 */
    STARTED("STARTED", true),

    /** 实例完成。 */
    COMPLETED("COMPLETED", true),

    /** 实例终止。 */
    TERMINATED("TERMINATED", true);

    private final String code;

    /** 是否为实例级触发。 */
    private final boolean instanceLevel;
}
