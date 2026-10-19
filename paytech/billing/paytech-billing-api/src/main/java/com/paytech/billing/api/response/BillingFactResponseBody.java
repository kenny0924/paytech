package com.paytech.billing.api.response;

import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 计费事实详情：BillingFactPayload 的全部字段，加登记与批准信息、发布状态、定价输入、行计算证据与行链接
 * <li>ID、版本与金额均为十进制字符串，金额按币种精度补齐；时间为 UTC。字段语义与 {@code BillingFactPayload} 一致。</li>
 */
@Getter
@Setter
public class BillingFactResponseBody {
    /** 计费事实 ID */
    private String factId;

    /**
     * 事实版本
     * <li>DRAFT 为 0；批准时定稿为 1 或被替代事实版本 + 1。</li>
     */
    private String factVersion;

    /** 被替代事实；为空表示首次登记 */
    private String supersedesFactId;

    /** 来源事实；为空表示无 */
    private String parentFactId;

    /** 计费事实类型代码（BillingFactType） */
    private String factType;

    /** 来源方式代码（FactOriginType：AUTO、MANUAL） */
    private String originType;

    /** 登记状态：DRAFT、APPROVED、SUPERSEDED */
    private String status;

    /** 来源系统代码 */
    private String sourceSystem;

    /** 来源事件键；人工登记为提交的 sourceReference */
    private String sourceEventKey;

    /** 来源业务事实版本；人工登记为 1 */
    private String sourceVersion;

    /** 来源载荷或提交内容的 RFC 8785 SHA-256 小写十六进制 */
    private String sourcePayloadHash;

    /**
     * 业务发生时间
     * <li>人工登记 DRAFT 为提交时间，批准时覆盖为批准时间；ledger 过账日期取它。</li>
     */
    private Instant businessTime;

    /** 事实定稿时间（等于 approvedTime）；DRAFT 为空 */
    private Instant recordedTime;

    /**
     * 余额到期时间
     * <li>仅 PAYMENT 且已计算时非空。</li>
     */
    private Instant balanceAvailableTime;

    /**
     * CCT Client ID
     * <li>资金类事实为空。</li>
     */
    private String clientId;

    /** Operating Entity 编号 */
    private String entityNo;

    /** Local Partner；可为空 */
    private String localPartnerId;

    /** 平台服务费收取方 Finance Entity；可为空 */
    private String financeEntityId;

    /** 币种 */
    private String currency;

    /** 业务事实非负总额，按币种精度补齐 */
    private String grossAmount;

    /** 编制人；AUTO 事实为空 */
    private String makerId;

    /** 复核人；未批准或 AUTO 事实为空 */
    private String checkerId;

    /** 批准（AUTO 为登记）时间；DRAFT 为空 */
    private Instant approvedTime;

    /** BillingFactRecorded 首次发布确认时间；未发布为空 */
    private Instant publishedAt;

    /**
     * 定价与结算输入
     * <li>仅 PAYMENT 非空；本期人工登记恒为空。</li>
     */
    private BillingFactPricingResponseBody pricing;

    /** 外部业务对象引用，按 refType 升序；无引用为空列表 */
    private List<BillingFactRefResponseBody> refs;

    /** 全部明细行，按行号升序 */
    private List<BillingFactLineResponseBody> lines;

    /**
     * 以本事实明细行为目标的行链接
     * <li>本期人工登记不产生链接，恒为空列表。</li>
     */
    private List<BillingFactLineLinkResponseBody> links;
}
