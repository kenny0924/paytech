package com.paytech.operations.console.bff.service.processor.request;

import com.paytech.compliance.api.request.QualificationManualDecisionRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** B10 手工登记资格的内部请求。 */
@Getter
@AllArgsConstructor
public class RecordManualQualificationRequest {

    /** 入站边界已校验的操作人身份 */
    @NotNull(message = "操作人身份不能为空")
    private final OperatorContext operator;

    /** 页面提交的登记内容，按 compliance 合同原样转发 */
    @Valid
    @NotNull(message = "登记内容不能为空")
    private final QualificationManualDecisionRequestBody decision;
}
