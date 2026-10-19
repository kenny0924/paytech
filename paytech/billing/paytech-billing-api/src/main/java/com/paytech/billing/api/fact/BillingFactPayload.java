package com.paytech.billing.api.fact;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 计费事实载荷：头、外部引用与全部明细行，消费者无需回查 billing
 * <li>同一份结构既是 BillingFactRecorded Schema v1 事件的 Payload（account 消费该事件并反序列化本类），也是 billing 按 ID 读取接口的字段
 * 语义（ledger 经读取接口取事实）；定价输入与行计算证据不进事件，只由读取接口返回。</li>
 * <li>事件信封：eventType = {@value #EVENT_TYPE}，schemaVersion = {@value #SCHEMA_VERSION}，aggregateType = {@value #AGGREGATE_TYPE}，
 * aggregateId = factId，aggregateVersion = factVersion，occurredAt = recordedTime，sourceService = billing。</li>
 * <li>事件 JSON 中所有标量都是字符串（ID、版本与金额为十进制字符串，时间为 UTC RFC 3339），缺省值为 JSON null，符合 Foundation
 * JSON Object Profile；Jackson 默认把它们绑定到本类的 Long / BigDecimal / Instant 字段。</li>
 * <li>businessTime 是 ledger 过账日期的唯一来源；refs 中 CLEARING_ACCOUNT 是 ledger 渠道账户（CHANNEL_ACCOUNT）Owner 的来源，
 * ledger 不读 reconciliation。</li>
 * <li>代码类字段以 String 承载稳定 Code，取值见对应枚举（{@code BillingFactType}、{@code FactOriginType}、{@code BillingFactStatus}、
 * {@code FactRefType}、{@code FactComponentType}、{@code FactPartyRole}、{@code FactLineDirection}）。</li>
 */
@Getter
@Setter
public class BillingFactPayload {
    /** BillingFactRecorded 事件类型，与事件目录 catalog/events.yaml 一致。 */
    public static final String EVENT_TYPE = "BillingFactRecorded";

    /** 当前 Payload Schema 版本。 */
    public static final int SCHEMA_VERSION = 1;

    /** 事件聚合类型：计费事实。 */
    public static final String AGGREGATE_TYPE = "BILLING_FACT";

    /** 计费事实主键（t_billing_fact.id），account 余额应用与 ledger 过账以它幂等。 */
    private Long factId;

    /**
     * 事实版本（fact_version）
     * <li>批准时定稿：首次登记为 1，替代事实为被替代事实版本 + 1；同时是事件 aggregateVersion。</li>
     */
    private Long factVersion;

    /** 本事实替代的前一计费事实；首次登记为空。消费方据此冲回被替代事实的影响后应用本事实。 */
    private Long supersedesFactId;

    /** 派生事实的来源事实（parent_fact_id）：退款、争议、重复扣款及其补偿、成本调整指向原支付事实，其余为空。 */
    private Long parentFactId;

    /** 计费事实类型代码，见 BillingFactType。 */
    private String factType;

    /** 来源方式代码（FactOriginType：AUTO、MANUAL）。 */
    private String originType;

    /** 登记状态代码（BillingFactStatus）；事件中只会是 APPROVED 或 SUPERSEDED。 */
    private String status;

    /** 发布或登记计费事实的来源系统代码。 */
    private String sourceSystem;

    /** 来源系统内稳定且可幂等消费的事件键；人工登记时为来源引用（如工单号）。 */
    private String sourceEventKey;

    /** 来源业务事实的单调递增版本；命令类与人工登记固定为 1。 */
    private Long sourceVersion;

    /** 来源载荷或命令请求的 RFC 8785 SHA-256 小写十六进制。 */
    private String sourcePayloadHash;

    /**
     * 业务发生的 UTC 微秒时间
     * <li>支付成功、退款成功、争议结论、银行确认、兑换成交或人工批准时间；ledger 过账日期与生效规则版本统一取它。</li>
     */
    private Instant businessTime;

    /** 事实登记定稿时间：批准（AUTO 为登记）的 UTC 微秒时间，即事件 occurredAt。 */
    private Instant recordedTime;

    /**
     * 余额到期时间：Client Principal / Local Partner Share 由 PENDING 成熟到 AVAILABLE 的 UTC 微秒时间
     * <li>仅 PAYMENT 且 billing 已计算时非空，其余为空（JSON null）。account 读取它决定到期转可用；ledger 不使用。</li>
     */
    private Instant balanceAvailableTime;

    /** CCT Client 稳定业务 ID；资金类事实（CHANNEL_SETTLEMENT、PAYOUT_SETTLED、TAX_PAID）为空。 */
    private Long clientId;

    /** Operating Entity 编号（entityNo），决定 account 入账流与 ledger OE 账簿。 */
    private String entityNo;

    /** Local Partner；无合作方为空。与 financeEntityId 同有同无。 */
    private Long localPartnerId;

    /** 平台服务费收取方 Finance Entity（ledger 主数据主键），决定平台账簿；无合作方为空。 */
    private Long financeEntityId;

    /** 全部金额的 ISO 4217 币种。 */
    private String currency;

    /** 业务事实非负总金额；人工 / 成本调整为全部明细行金额之和。 */
    private BigDecimal grossAmount;

    /** 外部业务对象引用，每种 refType 至多一条，按 refType 升序；无引用为空列表。 */
    private List<BillingFactRefPayload> refs;

    /** 全部明细行，按行号升序。 */
    private List<BillingFactLinePayload> lines;
}
