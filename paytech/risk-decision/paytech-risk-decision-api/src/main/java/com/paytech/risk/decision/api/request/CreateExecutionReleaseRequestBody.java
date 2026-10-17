package com.paytech.risk.decision.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 创建执行发布的请求。 */
public class CreateExecutionReleaseRequestBody {

    /**
     * 决策点编码
     * <li>必须支持运行期执行，一期为 PAYMENT_ATTEMPT_RISK。</li>
     */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private String pointCode;

    /**
     * 决策流版本 ID
     * <li>正整数十进制字符串；流程须属于该决策点且未删除，引用的规则集版本应已冻结。</li>
     */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String flowVersionId;

    /** 读取决策点编码。 */
    public String getPointCode() { return pointCode; }

    /** 设置决策点编码。 */
    public void setPointCode(String pointCode) { this.pointCode = pointCode; }

    /** 读取决策流版本 ID。 */
    public String getFlowVersionId() { return flowVersionId; }

    /** 设置决策流版本 ID。 */
    public void setFlowVersionId(String flowVersionId) { this.flowVersionId = flowVersionId; }
}
