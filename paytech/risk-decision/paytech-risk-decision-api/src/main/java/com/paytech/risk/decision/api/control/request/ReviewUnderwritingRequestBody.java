package com.paytech.risk.decision.api.control.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** HIGH 风险承保的四眼复核请求；复核人不能是提交人。 */
@Getter
@Setter
public class ReviewUnderwritingRequestBody {

    /** 期望的待复核版本并发版本。 */
    @NotNull
    @Positive
    private Integer expectedVersion;

    /** 复核结论：APPROVE 或 DECLINE。 */
    @NotBlank
    @Pattern(regexp = "APPROVE|DECLINE")
    private String decision;

    /**
     * 复核意见，最长 500 字符
     * <li>DECLINE 时必填并作为拒绝原因保存；APPROVE 时可空，保存为复核意见。</li>
     */
    @Size(max = 500)
    private String comment;
}
