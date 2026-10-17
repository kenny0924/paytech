package com.paytech.risk.api.decision.request;

import com.paytech.risk.api.validation.DictionaryText;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 字典维护请求，完整提交本次内容，不提供历史响应回放。 */
public class UpdateSubjectTagRequestBody {

    /**
     * 期望修订号。
     * <li>最近读取的正 BIGINT 十进制字符串，必须与当前修订号相同。</li>
     */
    @NotNull
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    @DecimalMax("9223372036854775807")
    private String expectedVersion;

    /**
     * 当前名称。
     * <li>1–100 个 Unicode 码点，不允许首尾空白。</li>
     */
    @DictionaryText(max = 100, trimmed = true)
    private String name;

    /**
     * 内部说明。
     * <li>键必填但允许 null；空串归一为 null，最多 500 个码点，可保留换行。</li>
     */
    @DictionaryText(max = 500, nullable = true, multiline = true)
    private String description;

    /** 返回期望修订号。 */
    public String getExpectedVersion() {
        return expectedVersion;
    }

    /** 设置期望修订号。 */
    public void setExpectedVersion(String expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    /** 返回当前名称。 */
    public String getName() {
        return name;
    }

    /** 设置当前名称。 */
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
}

