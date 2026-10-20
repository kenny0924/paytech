package com.paytech.billing.service.processor.request;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 提交计费事实（DRAFT）；代码类字段保持字符串，由 Processor 按目录解析。 */
@Getter
@Setter
public class SubmitBillingFactRequest {
    /** 计费事实类型代码：MANUAL_ADJUSTMENT 或 COST_ADJUSTMENT */
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

    /**
     * CCT Client
     * <li>MANUAL_ADJUSTMENT 必填；COST_ADJUSTMENT 可空，取来源事实的 Client。</li>
     */
    private Long clientId;

    /** Operating Entity 编号 */
    @NotBlank
    private String entityNo;

    /**
     * Local Partner
     * <li>与 financeEntityId 同有同无。</li>
     */
    private Long localPartnerId;

    /** 平台服务费收取方 Finance Entity */
    private Long financeEntityId;

    /** 币种 */
    @NotBlank
    private String currency;

    /** 编制人 Operations Account ID（X-Gateway-Actor-Subject） */
    @NotNull
    private Long makerId;

    /** 外部业务对象引用，可为空列表 */
    @NotNull
    @Size(max = 10)
    private List<@Valid @NotNull Ref> refs;

    /** 全部明细行，按提交顺序编号 */
    @NotNull
    @Size(min = 1, max = 100)
    private List<@Valid @NotNull Line> lines;

    /** 外部业务对象引用。 */
    @Getter
    @Setter
    public static class Ref {
        /** 引用类型代码（FactRefType） */
        @NotBlank
        private String refType;

        /** 引用标识 */
        @NotBlank
        private String refId;
    }

    /** 明细行。 */
    @Getter
    @Setter
    public static class Line {
        /** 组件类型代码（FactComponentType） */
        @NotBlank
        private String componentType;

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

        /**
         * 目标余额桶代码（PENDING、AVAILABLE）
         * <li>可为空。</li>
         */
        private String targetBucket;
    }
}
