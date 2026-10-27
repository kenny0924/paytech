package com.paytech.compliance.api.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** O07 退回修改：需求级错误、补件期限与客户可见说明。 */
@Getter
@Setter
public class ClientReviewReturnRequestBody {

    /** 退回原因代码（内部）。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private String reasonCode;

    /**
     * 需求级错误
     * <li>至少一项、最多 100 项；Client 门户据此逐项定位。</li>
     */
    @NotEmpty
    @Size(max = 100)
    @Valid
    private List<ClientReviewReturnErrorBody> errors;

    /**
     * 补件期限天数
     * <li>1—90；超过期限未重提，案件 EXPIRED，申请随之 WITHDRAWN。</li>
     */
    @NotNull
    @Min(1)
    @Max(90)
    private Integer dueDays;

    /**
     * 兼容字段：退回定位分区
     * <li>BASIC、BUSINESS、PEOPLE 或 MATERIALS，可空；保留一版供旧门户展示。</li>
     */
    @Pattern(regexp = "^(BASIC|BUSINESS|PEOPLE|MATERIALS)$")
    private String section;

    /** 客户可见说明，1—1000 字符。 */
    @NotBlank
    @Size(max = 1000)
    private String publicNote;

    /**
     * 代操作的责任 PayFac（Local Partner ID）
     * <li>平台运营代 PayFac 操作时填写；compliance 记 operated_by=PLATFORM 与该值，操作人仍为当前运营账号。</li>
     * <li>可空；ops-bff 负责校验该 Partner 存在且具 OE_PROVIDER 角色。</li>
     */
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String onBehalfOfPartnerId;
}
