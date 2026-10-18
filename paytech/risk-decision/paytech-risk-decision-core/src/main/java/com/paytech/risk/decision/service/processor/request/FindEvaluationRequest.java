package com.paytech.risk.decision.service.processor.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按风控点与业务身份查询正式评估的 Owner 输入；业务身份的具体格式由该点评估器的合同校验。 */
@Getter
@AllArgsConstructor
public class FindEvaluationRequest {
    /** 风控点编码。 */
    @NotNull
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private final String pointCode;

    /** 业务身份。 */
    @NotNull
    @Size(min = 1, max = 128)
    private final String businessKey;
}
