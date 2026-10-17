package com.paytech.risk.api.evaluation.request;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 统一风控评估请求；inputs 的字段集合由风控点输入合同决定，调用方可用对应输入类构造。 */
public class EvaluateDecisionRequestBody {
    /** 风控点编码，如 PAYMENT_ATTEMPT_RISK。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private String pointCode;

    /** 输入合同版本，从 1 开始。 */
    @NotNull
    @Positive
    private Integer contractVersion;

    /**
     * 业务身份
     * <li>同一风控点内唯一，格式由该点输入合同决定（支付为支付尝试 ID）。</li>
     */
    @NotBlank
    @Size(max = 64)
    private String businessKey;

    /**
     * 输入字段
     * <li>键为合同字段编码，值为字符串；可选字段未知时省略。</li>
     */
    @NotNull
    @Size(max = 32)
    private Map<String, String> inputs;

    /** 返回风控点编码。 */
    public String getPointCode() { return pointCode; }

    /** 设置风控点编码。 */
    public void setPointCode(String pointCode) { this.pointCode = pointCode; }

    /** 返回输入合同版本。 */
    public Integer getContractVersion() { return contractVersion; }

    /** 设置输入合同版本。 */
    public void setContractVersion(Integer contractVersion) { this.contractVersion = contractVersion; }

    /** 返回业务身份。 */
    public String getBusinessKey() { return businessKey; }

    /** 设置业务身份。 */
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }

    /** 返回输入字段。 */
    public Map<String, String> getInputs() { return inputs; }

    /** 设置输入字段。 */
    public void setInputs(Map<String, String> inputs) { this.inputs = inputs; }
}
