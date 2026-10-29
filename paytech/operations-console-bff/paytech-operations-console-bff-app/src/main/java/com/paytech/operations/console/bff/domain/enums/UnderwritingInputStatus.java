package com.paytech.operations.console.bff.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 承保工作台输入状态；Code 是工作台 inputStatus 字段的合同值。 */
public enum UnderwritingInputStatus implements CodeEnum {
    /** 输入齐备，可提交。 */
    READY("READY"),

    /** Client 没有已批准的入驻申请，尽调未批准，不能提交。 */
    DD_NOT_APPROVED("DD_NOT_APPROVED"),

    /** MCC / 行业分类尚未由运营确认，不能提交。 */
    CLASSIFICATION_NOT_CONFIRMED("CLASSIFICATION_NOT_CONFIRMED"),

    /** 有来源读取失败或超时，页面提示重试，不能提交。 */
    SOURCE_UNAVAILABLE("SOURCE_UNAVAILABLE");

    private final String code;

    UnderwritingInputStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
