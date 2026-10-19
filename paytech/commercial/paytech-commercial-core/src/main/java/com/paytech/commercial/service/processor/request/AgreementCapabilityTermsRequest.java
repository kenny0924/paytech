package com.paytech.commercial.service.processor.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.paytech.commercial.api.enums.CapabilityLimitCode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 开通项新增与修改共用的条款：生效区间、保证金与整体约束行。 */
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

    /** 约束行整体 */
    @NotNull
    @Valid
    private List<Limit> limits;

    /** 开通项约束的一个值 */
    @Getter
    @Setter
    public static class Limit {
        /** 约束类别 */
        @NotNull
        private CapabilityLimitCode limitCode;

        /** 规范化值 */
        @NotBlank
        private String limitValue;
    }
}
