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

/** 整体改写开通项的状态、区间、保证金与接受条件；内容不变时不产生变更。 */
@Getter
@Setter
public class UpdateAgreementCapabilityRequestBody {
    /**
     * 调用方读取到的合同 BCR Root 版本（changeVersion）
     * <li>本次写入以它做 CAS，提交后版本加一；不一致返回 409。</li>
     */
    @NotNull(message = "expectedChangeVersion 必填")
    @Positive(message = "expectedChangeVersion 必须为正")
    private Long expectedChangeVersion;

    /**
     * 合同成员资格
     * <li>ACTIVE 纳入合同，DISABLED 运营关闭；执行状态由前置门禁与暂停 / 恢复接口维护，不能在此设置。</li>
     * <li>DISABLED → ACTIVE（重新纳入）时，CLIENT 合同的开通项回到 PENDING_PREREQUISITES 并登记前置检查。</li>
     */
    @NotBlank(message = "status 必填")
    @Pattern(regexp = "ACTIVE|DISABLED", message = "status 只能为 ACTIVE、DISABLED")
    private String status;

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

    /**
     * 接受条件，整体提交，每个维度一项，不得重复
     * <li>维度须在该可开通项可配维度内，必填维度（CURRENCY）必须出现。</li>
     */
    @NotNull(message = "conditions 必填")
    @Size(max = 20, message = "conditions 最多 20 个维度")
    @Valid
    private List<CapabilityConditionEntry> conditions;

    /** 是否要求 3DS 认证 */
    @NotNull(message = "require3ds 必填")
    private Boolean require3ds;
}
