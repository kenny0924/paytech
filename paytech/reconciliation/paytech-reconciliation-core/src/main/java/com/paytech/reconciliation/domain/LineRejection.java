package com.paytech.reconciliation.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 完成导入校验的一条拒绝原因；行号为空表示账单头或整单层面的原因。 */
@Getter
@AllArgsConstructor
public class LineRejection {
    /** 行号；整单原因为空 */
    private final Integer lineNo;

    /** 可外显的中文原因 */
    private final String reason;
}
