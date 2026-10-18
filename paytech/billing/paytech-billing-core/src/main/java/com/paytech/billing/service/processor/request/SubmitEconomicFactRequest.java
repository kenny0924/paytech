package com.paytech.billing.service.processor.request;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 提交经济事实（DRAFT）；代码类字段保持字符串，由 Processor 按目录解析。 */
@Getter
@Setter
public class SubmitEconomicFactRequest {
    /** 经济事实类型代码：MANUAL_ADJUSTMENT 或 COST_ADJUSTMENT */
    @NotBlank
    private String factType;

    /** 来源系统代码 */
    @NotBlank
    private String sourceSystem;

    /** 来源引用 */
    @NotBlank
    private String sourceReference;

    /**
     * 来源事实
     * <li>COST_ADJUSTMENT 必填，MANUAL_ADJUSTMENT 必须为空。</li>
     */
    private Long parentFactId;

    /**
     * 被替代事实
     * <li>可为空。</li>
     */
    private Long supersedesFactId;

    /** CCT Client */
    @NotNull
    private Long clientId;

    /** Operating Entity 编号 */
    @NotBlank
    private String entityNo;

    /**
     * Local Partner
     * <li>与 partnerAgreementOeId、financeEntityId 全有或全无。</li>
     */
    private Long localPartnerId;

    /** Partner Agreement 与 OE 的关联 ID */
    private Long partnerAgreementOeId;

    /** 平台服务费收取方 Finance Entity */
    private Long financeEntityId;

    /** 币种 */
    @NotBlank
    private String currency;

    /**
     * Finance 渠道清算账户
     * <li>COST_ADJUSTMENT 必填。</li>
     */
    private Long channelAccountId;

    /** 编制人 */
    @NotNull
    private Long makerId;

    /** 全部明细行，按提交顺序编号 */
    @NotNull
    @Size(min = 1, max = 100)
    private List<@Valid @NotNull Line> lines;

    /** 明细行。 */
    @Getter
    @Setter
    public static class Line {
        /** 费用类型代码（AmountSource） */
        @NotBlank
        private String feeTypeCode;

        /** 付方角色代码（FactPartyRole） */
        @NotBlank
        private String payerRole;

        /** 付方标识 */
        @NotBlank
        private String payerId;

        /** 收方角色代码 */
        @NotBlank
        private String payeeRole;

        /** 收方标识 */
        @NotBlank
        private String payeeId;

        /** 金额，币种主单位 */
        @NotNull
        private BigDecimal amount;

        /** 影响方向代码（FactLineDirection） */
        @NotBlank
        private String direction;
    }
}
