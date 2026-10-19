package com.paytech.billing.api.fact;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 经济事实载荷：头与全部明细行，消费者无需回查 billing（design/04 §2、§5）
 * <li>同一份结构既是 billing 经济事实读取接口的明细，也是 EconomicFactRecorded Schema v1 事件的 Payload（account 消费该事件并反序列化本类；
 * ledger 经读取接口取事实头与明细）。</li>
 * <li>事件信封：eventType = {@value #EVENT_TYPE}，schemaVersion = {@value #SCHEMA_VERSION}，aggregateType = {@value #AGGREGATE_TYPE}，
 * aggregateId = factId，aggregateVersion = factVersion，occurredAt = recordedTime，sourceService = billing。</li>
 * <li>事件 JSON 中所有标量都是字符串（ID、版本与金额为十进制字符串，时间为 UTC RFC 3339），缺省值为 JSON null，符合 Foundation
 * JSON Object Profile；Jackson 默认把它们绑定到本类的 Long / BigDecimal / Instant 字段。</li>
 * <li>金额与舍入已由 billing 一次决定；渠道清算账户主键由 billing 在头中给出，ledger 不读 reconciliation。</li>
 * <li>代码类字段以 String 承载稳定 Code，取值见对应枚举（如 {@code EconomicsType}、{@code AmountSource}、{@code FactPartyRole}）。</li>
 */
@Getter
@Setter
public class EconomicFactPayload {
    /** EconomicFactRecorded 事件类型，与事件目录 catalog/events.yaml 一致。 */
    public static final String EVENT_TYPE = "EconomicFactRecorded";

    /** 当前 Payload Schema 版本。 */
    public static final int SCHEMA_VERSION = 1;

    /** 事件聚合类型：经济事实。 */
    public static final String AGGREGATE_TYPE = "ECONOMIC_FACT";

    /** 经济事实主键（t_economic_fact.id），ledger 自动指令与 account 余额应用以它幂等。 */
    private Long factId;

    /**
     * 事实版本（fact_version）
     * <li>批准时定稿：首次登记为 1，替代事实为被替代事实版本 + 1；同时是事件 aggregateVersion。</li>
     */
    private Long factVersion;

    /** 本事实替代的前一经济事实；首次登记为空。消费方据此冲回被替代事实的影响后应用本事实。 */
    private Long supersedesFactId;

    /** 派生事实的来源事实（parent_economics_id）；COST_ADJUSTMENT 指向原支付事实，其余可为空。 */
    private Long parentFactId;

    /** 经济事实类型代码，见 EconomicsType。 */
    private String factType;

    /** 发布或登记经济事实的来源系统代码。 */
    private String sourceSystem;

    /** 来源系统内稳定且可幂等消费的事件键；人工登记时为来源引用（如工单号）。 */
    private String sourceEventKey;

    /** 来源业务事实的单调递增版本；人工登记固定为 1。 */
    private Long sourceVersion;

    /** 来源事件规范化载荷的不可变摘要；人工登记为提交内容的 RFC 8785 SHA-256 小写十六进制。 */
    private String sourcePayloadHash;

    /** 事实生效时间：人工 / 成本调整为 Checker 批准的 UTC 微秒时间，即事件 occurredAt。 */
    private Instant recordedTime;

    /** 仅 PAYMENT：支付成功的 UTC 微秒时间，决定过账日期与生效规则版本。 */
    private Instant paymentSucceededTime;

    /**
     * 余额到期时间：Client Principal / Local Partner Share 由 PENDING 成熟到 AVAILABLE 的 UTC 微秒时间
     * <li>仅 PAYMENT 且 billing 已计算时非空，其余为空（JSON null）；Schema 版本保持 1，新增可选字段向后兼容。</li>
     * <li>account 读取它决定到期转可用；ledger 不使用。</li>
     */
    private Instant balanceAvailableTime;

    /** CCT Client 稳定业务 ID。 */
    private Long clientId;

    /** Operating Entity 编号（entityNo），决定 OE 账簿。 */
    private String entityNo;

    /** Local Partner；无合作方为空。与 partnerAgreementOeId、financeEntityId 全有或全无。 */
    private Long localPartnerId;

    /** Partner Agreement 与 OE 的关联 ID；无合作方为空。 */
    private Long partnerAgreementOeId;

    /** 平台服务费收取方 Finance Entity（ledger 主数据主键），决定平台账簿；无合作方为空。 */
    private Long financeEntityId;

    /** 全部金额的 ISO 4217 币种。 */
    private String currency;

    /** 业务事实非负总金额；人工 / 成本调整为全部明细行金额之和。 */
    private BigDecimal grossAmount;

    /** billing 给出的 Finance 渠道清算账户主键（reconciliation t_channel_account.id）；规则引用 CHANNEL_ACCOUNT 角色时必填。 */
    private Long channelAccountId;

    /** 全部明细行，按行号升序。 */
    private List<EconomicFactLinePayload> lines;
}
