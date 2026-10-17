package com.paytech.compliance.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** O05 材料核验结果；客户可见说明在退回或最终决定时另行提交。 */
public class ClientReviewEvidenceReviewRequestBody {

    /**
     * 核验结果
     * <li>ACCEPTED 或 REJECTED。</li>
     */
    @NotBlank
    @Pattern(regexp = "^(ACCEPTED|REJECTED)$")
    private String outcome;

    /** 读取核验结果。 */
    public String getOutcome() {
        return outcome;
    }

    /** 设置核验结果。 */
    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }
}
