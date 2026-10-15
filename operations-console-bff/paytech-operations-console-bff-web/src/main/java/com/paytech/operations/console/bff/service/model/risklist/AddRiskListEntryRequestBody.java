package com.paytech.operations.console.bff.service.model.risklist;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 向名单加入成员；主体原值只转发给 Risk，BFF 不记录、不回显。
 */
@Getter
@Setter
public class AddRiskListEntryRequestBody {
    /**
     * 主体原值
     * <li>受限值；由 Risk 按主体类型规范化，响应只返回脱敏键。</li>
     */
    @NotBlank(message = "主体值必填")
    @Size(max = 256, message = "主体值不超过 256 个字符")
    private String subjectValue;

    /**
     * 生效起点
     * <li>UTC ISO-8601；为空表示立即生效。</li>
     */
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,6})?Z", message = "生效起点格式无效")
    private String effectiveFrom;

    /**
     * 生效终点（不含）
     * <li>UTC ISO-8601；为空表示长期有效。</li>
     */
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,6})?Z", message = "生效终点格式无效")
    private String effectiveTo;

    /**
     * 加入原因
     */
    @NotBlank(message = "加入原因必填")
    @Size(max = 500, message = "加入原因不超过 500 个字符")
    private String reason;
}
