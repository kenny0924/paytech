package com.paytech.commercial.api.term.request;

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

/** 一条价格：准确 OE + 交易币种 + 定价画像，含结算四字段与计算组件。 */
@Getter
@Setter
public class CommercialTermPriceRequestBody {
    /** 准确 OE，必须被版本合同覆盖。 */
    @NotBlank(message = "entityNo 必填")
    @Pattern(regexp = "[A-Z]{2}[0-9]{3}", message = "entityNo 格式不正确")
    private String entityNo;

    /** 交易币种即费用币种（ISO 4217）；同一画像按币种各一条，不做通配。 */
    @NotBlank(message = "currency 必填")
    @Pattern(regexp = "[A-Z]{3}", message = "currency 格式不正确")
    private String currency;

    /** 定价画像代码；排期与激活时必须 ACTIVE。 */
    @NotBlank(message = "pricingProfileCode 必填")
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}", message = "pricingProfileCode 格式不正确")
    private String pricingProfileCode;

    /** 相同具体度候选的确定性顺序，越小越优先；同一 OE + 币种内唯一。 */
    @NotNull(message = "priorityNo 必填")
    @Min(value = 1, message = "priorityNo 必须为正整数")
    private Integer priorityNo;

    /**
     * T+N 工作日结算天数
     * <li>Client 条款四个结算字段全部必填；Partner 条款全部为空。</li>
     */
    @Min(value = 0, message = "settlementBusinessDays 不能为负")
    @Max(value = 365, message = "settlementBusinessDays 不能超过 365")
    private Integer settlementBusinessDays;

    /** 结算工作日日历代码，本期固定 AE_BUSINESS_DAYS。 */
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "businessCalendarCode 格式不正确")
    private String businessCalendarCode;

    /** 结算时区（IANA），本期仅 UTC。 */
    @Size(max = 64, message = "settlementTimezone 过长")
    private String settlementTimezone;

    /** 结算基准日的本地日内截止秒数，0–86399。 */
    @Min(value = 0, message = "cutoffSecondOfDay 不能为负")
    @Max(value = 86399, message = "cutoffSecondOfDay 不能超过 86399")
    private Integer cutoffSecondOfDay;

    /** 计算组件，按列表顺序编号（component_no 从 1 连续），多个组件相加。 */
    @NotEmpty(message = "components 至少一项")
    @Size(max = 50, message = "components 最多 50 项")
    @Valid
    private List<CommercialTermComponentRequestBody> components;
}
