package com.paytech.commercial.api.term.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 已批准版本排期：写入生效起点并执行激活前校验。 */
@Getter
@Setter
public class ScheduleCommercialTermVersionRequestBody {
    /** 方案 BCR 根当前修订版本（CAS 令牌）；不等于当前值时 409。 */
    @NotBlank(message = "expectedChangeVersion 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "expectedChangeVersion 格式不正确")
    private String expectedChangeVersion;


    /**
     * 生效起点
     * <li>UTC RFC 3339，最多六位小数；不早于当前时间，晚于同方案已排期 / 生效 / 被替代版本的起点，且在合同生效区间内。</li>
     */
    @NotBlank(message = "effectiveFrom 必填")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,6})?Z", message = "effectiveFrom 必须为 UTC RFC 3339")
    private String effectiveFrom;
}
