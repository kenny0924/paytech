package com.paytech.commercial.api.agreement.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 在合同覆盖的准确 OE 下新增开通项（初始 ENABLED）。
 * <li>(agreementId, entityNo, capabilityCode) 唯一，重复返回 409。</li>
 */
@Getter
@Setter
public class AddAgreementCapabilityRequestBody {
    /**
     * 调用方读取到的合同 BCR Root 版本（changeVersion）
     * <li>本次写入以它做 CAS，提交后版本加一；不一致返回 409。</li>
     */
    @NotNull(message = "expectedChangeVersion 必填")
    @Positive(message = "expectedChangeVersion 必须为正")
    private Long expectedChangeVersion;

    /**
     * 开通项适用的准确 OE
     * <li>CLIENT 合同须等于合同 OE，PARTNER 须在合同覆盖 OE 内。</li>
     */
    @NotBlank(message = "entityNo 必填")
    @Pattern(regexp = "[A-Z]{2}[0-9]{3}", message = "entityNo 必须是 5 位 OE 编号")
    private String entityNo;

    /** 可开通项代码（产品目录 t_product_capability），引用时须 ACTIVE 且所属产品已订阅或为基座产品 */
    @NotBlank(message = "capabilityCode 必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "capabilityCode 必须是大写稳定代码")
    private String capabilityCode;

    /** 开通生效起点（含），带时区偏移的 ISO 8601 */
    @NotBlank(message = "effectiveFrom 必填")
    @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,6})?(Z|[+-][0-9]{2}:[0-9]{2})", message = "effectiveFrom 必须是带时区偏移的 ISO 8601 时间")
    private String effectiveFrom;

    /** 开通生效终点（不含），可空；必须晚于 effectiveFrom */
    @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,6})?(Z|[+-][0-9]{2}:[0-9]{2})", message = "effectiveTo 必须是带时区偏移的 ISO 8601 时间")
    private String effectiveTo;

    /**
     * 滚动保证金比例，0–1 的十进制字符串
     * <li>与 rollingReserveDays 成组为空或非空。</li>
     */
    @Pattern(regexp = "(0(\\.[0-9]{1,10})?|1(\\.0{1,10})?)", message = "rollingReserveRate 必须是 0 到 1 的十进制字符串，最多 10 位小数")
    private String rollingReserveRate;

    /**
     * 滚动保证金持有天数，1–3650
     * <li>与 rollingReserveRate 成组为空或非空。</li>
     */
    @Min(value = 1, message = "rollingReserveDays 最小为 1")
    @Max(value = 3650, message = "rollingReserveDays 最大为 3650")
    private Integer rollingReserveDays;

    /** 约束行，整体提交，最多 50 条，不得重复 */
    @NotNull(message = "limits 必填，可为空列表")
    @Size(max = 50, message = "limits 最多 50 条")
    @Valid
    private List<CapabilityLimitEntry> limits;
}
