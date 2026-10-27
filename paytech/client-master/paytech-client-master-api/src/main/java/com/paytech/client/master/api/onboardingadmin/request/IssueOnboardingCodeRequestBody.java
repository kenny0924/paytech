package com.paytech.client.master.api.onboardingadmin.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** CM-12 签发入驻码；码由服务端随机生成。 */
@Getter
@Setter
public class IssueOnboardingCodeRequestBody {
    /** 生效 UTC 时间（ISO-8601），可空；为空即刻生效。 */
    @Size(max = 40, message = "effectiveFrom 格式不正确")
    private String effectiveFrom;

    /** 失效 UTC 时间（ISO-8601），可空；须晚于生效时间。 */
    @Size(max = 40, message = "effectiveTo 格式不正确")
    private String effectiveTo;

    /** 最大使用次数，可空表示不限；1 到 100000。 */
    @Min(value = 1, message = "maxUses 至少为 1")
    @Max(value = 100000, message = "maxUses 不能超过 100000")
    private Integer maxUses;

    /** 签发说明，可空。 */
    @Size(max = 255, message = "note 不能超过 255 个字符")
    private String note;
}
