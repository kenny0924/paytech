package com.paytech.platform.support.workflow.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 动作运行状态，与 t_workflow_action_run.status 一致。
 *
 * <p>2026-10-22 去掉动作验证：VERIFIED 改为 COMPLETED，UNVERIFIED 删除（存量迁移为 PENDING）。</p>
 */
@Getter
@RequiredArgsConstructor
public enum ActionRunStatus implements CodeEnum {

    /** 待处理：尚未完成。 */
    PENDING("PENDING"),

    /** 已完成：执行人点「完成」且通过必填与技术检查；必选动作在节点进入审批前可再次完成。 */
    COMPLETED("COMPLETED"),

    /** 已取消：节点结束或实例终止时仍未完成，由系统设置。 */
    CANCELLED("CANCELLED");

    private final String code;
}
