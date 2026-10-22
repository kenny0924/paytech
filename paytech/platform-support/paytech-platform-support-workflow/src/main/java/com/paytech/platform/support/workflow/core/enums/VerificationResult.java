package com.paytech.platform.support.workflow.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 一次验证的结论，与 t_workflow_verification.result 一致。 */
@Getter
@RequiredArgsConstructor
public enum VerificationResult implements CodeEnum {

    /** 通过。 */
    PASSED("PASSED"),

    /** 未通过。 */
    FAILED("FAILED");

    private final String code;
}
