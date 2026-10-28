package com.paytech.commercial.service.processor.response;

import java.util.List;

import com.paytech.commercial.domain.CapabilityPrerequisiteDecision;

import lombok.Getter;
import lombok.Setter;

/** 开通项前置核对的内部结果。 */
@Getter
@Setter
public class CapabilityPrerequisiteCheckResponse {
    /** 合同 ID */
    private long agreementId;

    /** 核对后的合同 BCR Root 当前版本 */
    private long changeVersion;

    /** 本次有执行状态迁移 */
    private boolean changed;

    /** 任一成员开通项有 UNKNOWN 结论（资格查询失败） */
    private boolean unknown;

    /** 逐开通项判定，按开通项 ID 升序；executionStatus 取 targetExecutionStatus */
    private List<CapabilityPrerequisiteDecision> decisions;
}
