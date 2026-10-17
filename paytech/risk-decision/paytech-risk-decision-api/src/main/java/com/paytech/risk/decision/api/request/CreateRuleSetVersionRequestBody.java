package com.paytech.risk.api.decision.request;

import static com.paytech.risk.api.decision.DecisionRuleSetManagementModels.CONTENT_HASH;

import static com.paytech.risk.api.decision.DecisionRuleSetManagementModels.POSITIVE_DECIMAL_ID;

import static com.paytech.risk.api.decision.DecisionRuleSetManagementModels.CODE;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.RuleRequestBody;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 规则集管理请求。 */
public class CreateRuleSetVersionRequestBody {

    /** 决策事件点编码。 */
    @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Size(max = 64) @Pattern(regexp = CODE)
    private String pointCode;

    /** 规则集编码。 */
    @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Size(max = 64) @Pattern(regexp = CODE)
    private String ruleSetCode;

    /** 规则集名称。 */
    @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Size(max = 255)
    private String ruleSetName;

    /** 编辑基准版本 ID；首次创建传 null。 */
    @JsonProperty(isRequired = OptBoolean.TRUE) @Pattern(regexp = POSITIVE_DECIMAL_ID)
    private String baseRuleSetVersionId;

    /** 编辑基准内容 Hash；首次创建传 null。 */
    @JsonProperty(isRequired = OptBoolean.TRUE) @Pattern(regexp = CONTENT_HASH)
    private String baseContentHash;

    /** 本次变更原因。 */
    @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Size(max = 500)
    private String changeReason;

    /** 按优先级配置的规则。 */
    @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull @Size(min = 1, max = 200)
    private List<@NotNull @Valid RuleRequestBody> rules;

    /** 新保存必须显式指定 VARIABLE_REFERENCE。 */
    @Pattern(regexp = "^VARIABLE_REFERENCE$")
    private String operandFormat;

    /** JSON 反序列化构造。 */
    public CreateRuleSetVersionRequestBody() { }

    /** 按完整合同构造。 */
    public CreateRuleSetVersionRequestBody(String pointCode, String ruleSetCode, String ruleSetName, String baseRuleSetVersionId, String baseContentHash, String changeReason, List<RuleRequestBody> rules, String operandFormat) {
        setPointCode(pointCode);
        setRuleSetCode(ruleSetCode);
        setRuleSetName(ruleSetName);
        setBaseRuleSetVersionId(baseRuleSetVersionId);
        setBaseContentHash(baseContentHash);
        setChangeReason(changeReason);
        setRules(rules);
        setOperandFormat(operandFormat);
    }

    /** 按明确的历史字段组合构造，未提供的新字段保持空值。 */
    public CreateRuleSetVersionRequestBody(String pointCode, String ruleSetCode, String ruleSetName,
        String baseRuleSetVersionId, String baseContentHash, String changeReason, List<RuleRequestBody> rules) {
        this(pointCode, ruleSetCode, ruleSetName, baseRuleSetVersionId, baseContentHash, changeReason, rules, null);
    }

    /** 返回决策事件点编码。 */
    public String getPointCode() { return pointCode; }

    /** 设置决策事件点编码。 */
    public void setPointCode(String pointCode) {
        this.pointCode = pointCode;
    }

    /** 返回规则集编码。 */
    public String getRuleSetCode() { return ruleSetCode; }

    /** 设置规则集编码。 */
    public void setRuleSetCode(String ruleSetCode) {
        this.ruleSetCode = ruleSetCode;
    }

    /** 返回规则集名称。 */
    public String getRuleSetName() { return ruleSetName; }

    /** 设置规则集名称。 */
    public void setRuleSetName(String ruleSetName) {
        this.ruleSetName = ruleSetName;
    }

    /** 返回编辑基准版本 ID；首次创建传 null。 */
    public String getBaseRuleSetVersionId() { return baseRuleSetVersionId; }

    /** 设置编辑基准版本 ID；首次创建传 null。 */
    public void setBaseRuleSetVersionId(String baseRuleSetVersionId) {
        this.baseRuleSetVersionId = baseRuleSetVersionId;
    }

    /** 返回编辑基准内容 Hash；首次创建传 null。 */
    public String getBaseContentHash() { return baseContentHash; }

    /** 设置编辑基准内容 Hash；首次创建传 null。 */
    public void setBaseContentHash(String baseContentHash) {
        this.baseContentHash = baseContentHash;
    }

    /** 返回本次变更原因。 */
    public String getChangeReason() { return changeReason; }

    /** 设置本次变更原因。 */
    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }

    /** 返回按优先级配置的规则。 */
    public List<RuleRequestBody> getRules() { return rules; }

    /** 设置按优先级配置的规则。 */
    public void setRules(List<RuleRequestBody> rules) {
        this.rules = rules == null ? null : List.copyOf(rules);
    }

    /** 返回新保存必须显式指定 VARIABLE_REFERENCE。 */
    public String getOperandFormat() { return operandFormat; }

    /** 设置新保存必须显式指定 VARIABLE_REFERENCE。 */
    public void setOperandFormat(String operandFormat) {
        this.operandFormat = operandFormat;
    }
}
