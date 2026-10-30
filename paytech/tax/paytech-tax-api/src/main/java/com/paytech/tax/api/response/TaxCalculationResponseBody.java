package com.paytech.tax.api.response;

import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 一次税务计算
 * <li>ID 与金额为十进制字符串，金额按币种精度补齐；时间为 UTC。</li>
 * <li>BLOCKED 时 taxAmount、roundingAmount 与行税额为空（不以零表示成功）；无法得知的一侧金额为空：EXCLUSIVE 时 grossAmount 为空，
 * INCLUSIVE 时 netAmount 为空。</li>
 * <li>NOT_MANAGED 时头部税额为自报值、没有组件，行税额与行内无法得知的一侧金额为空。</li>
 */
@Getter
@Setter
public class TaxCalculationResponseBody {
    /** 计算 ID */
    private String calculationId;

    /** 来源系统 */
    private String sourceSystem;

    /** 来源对象类型：ORDER */
    private String sourceObjectType;

    /** 来源对象 ID */
    private String sourceObjectId;

    /** 来源对象公开编号（订单号） */
    private String sourceObjectNo;

    /** 来源准备版本 */
    private Integer sourceRevision;

    /** 税务范围键：SALE */
    private String taxScopeKey;

    /** 计算阶段（TaxCalculationStage） */
    private String stage;

    /** 结果状态（TaxCalculationStatus） */
    private String status;

    /**
     * 不由平台计税的原因（TaxNotManagedReason）
     * <li>只在 status = NOT_MANAGED 时非空：SUBJECT_NOT_REGISTERED（责任方不是平台登记的纳税主体）或 TAX_RESPONSIBLE_UNRESOLVED
     * （调用方未传责任方）。</li>
     */
    private String notManagedReason;

    /** 税额来源（TaxAmountSource） */
    private String taxAmountSource;

    /** 供应类型：SALE */
    private String supplyKind;

    /** 商业模式代码 */
    private String businessMode;

    /** 卖方 Party ID；可空 */
    private String sellerPartyId;

    /** 税务责任方 Party ID；调用方未确定责任方时为空 */
    private String taxSubjectPartyId;

    /** 买方 Client ID */
    private String buyerClientId;

    /** 收单 Operating Entity 编号 */
    private String entityNo;

    /** 币种 */
    private String currency;

    /** 行价格口径（TaxPriceMode） */
    private String priceMode;

    /** 候选税点日期 yyyy-MM-dd */
    private String taxPointDate;

    /** 税点依据：本期 PAYMENT（预付 SaaS 以付款为税点） */
    private String taxPointBasis;

    /** 行净额合计；INCLUSIVE 且 BLOCKED 时为空 */
    private String netAmount;

    /** 税额合计；BLOCKED 为空 */
    private String taxAmount;

    /**
     * 头级尾差
     * <li>逐行取整、尾差记在行上，本期恒为 0；BLOCKED 为空。</li>
     */
    private String roundingAmount;

    /** 应付总额 = netAmount + taxAmount + roundingAmount；EXCLUSIVE 且 BLOCKED 时为空 */
    private String grossAmount;

    /**
     * 报价有效期
     * <li>只有 FINAL 且 READY 有值（计算时间 + 地区包 taxPoint.quoteValidityHours，缺省 24 小时）；调用方支付前须校验未过期。</li>
     */
    private Instant expiresAt;

    /** 计算保存时间 */
    private Instant calculatedAt;

    /** BLOCKED 原因；其他状态为空列表 */
    private List<TaxBlockReasonResponseBody> blockReasons;

    /** 本次计算冻结的国家证据 */
    private List<TaxEvidenceResponseBody> evidence;

    /** 逐行结果，按 lineNo 升序 */
    private List<TaxCalculationLineResponseBody> lines;

    /**
     * 按辖区 + 注册 + 纳税主体汇总的应收税组件
     * <li>只含税额大于 0 的组件；可原样作为 OrderPaymentSucceeded.taxComponents。BLOCKED 与 NOT_MANAGED 为空列表。</li>
     */
    private List<TaxComponentSummaryResponseBody> components;
}
