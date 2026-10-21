package com.paytech.commercial.service.processor.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.paytech.commercial.api.enums.PricingDimension;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 开通项新增与修改共用的条款：生效区间、保证金、整体接受条件与 3DS 要求。 */
@Getter
@Setter
public abstract class AgreementCapabilityTermsRequest {
    /** 开通生效起点（含） */
    @NotNull
    private Instant effectiveFrom;

    /** 开通生效终点（不含），可空 */
    private Instant effectiveTo;

    /** 滚动保证金比例，可空，与天数成组 */
    private BigDecimal rollingReserveRate;

    /** 滚动保证金天数，可空，与比例成组 */
    private Integer rollingReserveDays;

    /** 接受条件整体，每个维度一项 */
    @NotNull
    @Valid
    private List<Condition> conditions;

    /** 是否要求 3DS 认证 */
    @NotNull
    private Boolean require3ds;

    /** 开通项的一个接受条件：同维度多值按 OR */
    @Getter
    @Setter
    public static class Condition {
        /** 维度 */
        @NotNull
        private PricingDimension dimensionCode;

        /** 取值，至少一个 */
        @NotEmpty
        private List<@NotBlank String> values;
    }
}
