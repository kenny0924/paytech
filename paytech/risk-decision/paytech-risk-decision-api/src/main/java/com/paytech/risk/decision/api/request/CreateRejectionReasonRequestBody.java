package com.paytech.risk.api.decision.request;

import com.paytech.risk.api.validation.DictionaryText;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 创建并启用拒绝原因，编码永久唯一，重复提交不回放历史结果。 */
public class CreateRejectionReasonRequestBody {

    /**
     * 永久原因编码。
     * <li>1–64 位，以大写字母开头，仅允许大写字母、数字和下划线。</li>
     */
    @NotNull
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private String reasonCode;

    /**
     * 原因名称。
     * <li>1–100 个 Unicode 码点，不允许首尾空白或控制字符。</li>
     */
    @DictionaryText(max = 100, trimmed = true)
    private String name;

    /**
     * 内部说明。
     * <li>键必填，允许 null；空串归一为 null，最多 500 个码点，可保留换行。</li>
     */
    @DictionaryText(max = 500, nullable = true, multiline = true)
    private String description;

    /**
     * 默认内部文案。
     * <li>1–500 个码点，必须含非空白字符，可保留换行，不回写历史规则。</li>
     */
    @DictionaryText(max = 500, multiline = true)
    private String defaultMessage;

    /** 返回永久原因编码。 */
    public String getReasonCode() {
        return reasonCode;
    }

    /** 设置永久原因编码。 */
    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    /** 返回原因名称。 */
    public String getName() {
        return name;
    }

    /** 设置原因名称。 */
    public void setName(String name) {
        this.name = name;
    }

    /** 返回内部说明。 */
    public String getDescription() {
        return description;
    }

    /** 设置内部说明。 */
    public void setDescription(String description) {
        this.description = description;
    }

    /** 返回默认内部文案。 */
    public String getDefaultMessage() {
        return defaultMessage;
    }

    /** 设置默认内部文案。 */
    public void setDefaultMessage(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }
}

