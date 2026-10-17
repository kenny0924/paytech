package com.paytech.risk.api.evaluation.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Manual Payout 某一检查点的完整资格评估范围。 */
public class ManualPayoutEligibilityRequestBody {

    /**
     * 调用方命令键
     * <li>同一检查点命令的重放必须复用原值；不是 HTTP Idempotency-Key。</li>
     */
    @NotBlank
    @Size(max = 128)
    private String commandKey;

    /**
     * 出款检查点
     * <li>PAYOUT_CREATION / PAYOUT_FINAL_APPROVAL / PAYOUT_EXECUTION_CREATION / PAYOUT_EXTERNAL_SUBMISSION。</li>
     */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$")
    @Size(max = 40)
    private String checkpointType;

    /**
     * 出款角色类型
     * <li>CLIENT 或 LOCAL_PARTNER。</li>
     */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$")
    @Size(max = 32)
    private String roleType;

    /** 出款角色 ID，正 BIGINT 十进制字符串。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String roleId;

    /** Operating Entity ID，正 BIGINT 十进制字符串。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String operatingEntityId;

    /**
     * 出款总额
     * <li>币种主单位的十进制字符串，大于 0，最多 16 位整数、8 位小数，不带无意义尾随零。</li>
     */
    @NotBlank
    @Pattern(regexp = "^(0|[1-9][0-9]{0,15})(\\.[0-9]{0,7}[1-9])?$")
    @DecimalMin(value = "0", inclusive = false)
    private String grossAmount;

    /** ISO 4217 币种。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z]{3}$")
    private String currency;

    /** 收款银行账户国家，ISO 3166-1 alpha-2。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z]{2}$")
    private String bankCountry;

    /**
     * 来源事实类型
     * <li>创建、最终审批检查点为 PAYOUT；Execution 创建、外部提交检查点为 PAYOUT_EXECUTION。</li>
     */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$")
    @Size(max = 32)
    private String sourceFactType;

    /** 来源事实 ID，正 BIGINT 十进制字符串。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String sourceFactId;

    /**
     * 来源事实版本
     * <li>创建类检查点必须为 1。</li>
     */
    @NotNull
    @Min(1)
    @Max(2147483647)
    private Integer sourceFactVersion;

    /**
     * 标准输入摘要
     * <li>小写十六进制 SHA-256，必须与 Risk 按 CODE-MOR:RISK:SETTLEMENT_ELIGIBILITY_INPUT:1 计算的结果一致。</li>
     */
    @NotBlank
    @Pattern(regexp = "^[a-f0-9]{64}$")
    private String inputHash;

    /** 读取调用方命令键。 */
    public String getCommandKey() { return commandKey; }

    /** 设置调用方命令键。 */
    public void setCommandKey(String commandKey) { this.commandKey = commandKey; }

    /** 读取出款检查点。 */
    public String getCheckpointType() { return checkpointType; }

    /** 设置出款检查点。 */
    public void setCheckpointType(String checkpointType) { this.checkpointType = checkpointType; }

    /** 读取出款角色类型。 */
    public String getRoleType() { return roleType; }

    /** 设置出款角色类型。 */
    public void setRoleType(String roleType) { this.roleType = roleType; }

    /** 读取出款角色 ID。 */
    public String getRoleId() { return roleId; }

    /** 设置出款角色 ID。 */
    public void setRoleId(String roleId) { this.roleId = roleId; }

    /** 读取 Operating Entity ID。 */
    public String getOperatingEntityId() { return operatingEntityId; }

    /** 设置 Operating Entity ID。 */
    public void setOperatingEntityId(String operatingEntityId) { this.operatingEntityId = operatingEntityId; }

    /** 读取出款总额。 */
    public String getGrossAmount() { return grossAmount; }

    /** 设置出款总额。 */
    public void setGrossAmount(String grossAmount) { this.grossAmount = grossAmount; }

    /** 读取币种。 */
    public String getCurrency() { return currency; }

    /** 设置币种。 */
    public void setCurrency(String currency) { this.currency = currency; }

    /** 读取收款银行账户国家。 */
    public String getBankCountry() { return bankCountry; }

    /** 设置收款银行账户国家。 */
    public void setBankCountry(String bankCountry) { this.bankCountry = bankCountry; }

    /** 读取来源事实类型。 */
    public String getSourceFactType() { return sourceFactType; }

    /** 设置来源事实类型。 */
    public void setSourceFactType(String sourceFactType) { this.sourceFactType = sourceFactType; }

    /** 读取来源事实 ID。 */
    public String getSourceFactId() { return sourceFactId; }

    /** 设置来源事实 ID。 */
    public void setSourceFactId(String sourceFactId) { this.sourceFactId = sourceFactId; }

    /** 读取来源事实版本。 */
    public Integer getSourceFactVersion() { return sourceFactVersion; }

    /** 设置来源事实版本。 */
    public void setSourceFactVersion(Integer sourceFactVersion) { this.sourceFactVersion = sourceFactVersion; }

    /** 读取标准输入摘要。 */
    public String getInputHash() { return inputHash; }

    /** 设置标准输入摘要。 */
    public void setInputHash(String inputHash) { this.inputHash = inputHash; }
}
