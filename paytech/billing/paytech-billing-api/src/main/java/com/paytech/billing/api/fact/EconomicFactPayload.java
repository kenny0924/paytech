package com.paytech.billing.api.fact;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 经济事实载荷：头与全部明细行，消费者无需回查 billing（design/04 §2、§5）
 * <li>本步作为 ledger 交易自动记账的输入 DTO；第 11a 步作为 EconomicFactRecorded 事件 Payload 发布。</li>
 * <li>金额与舍入已由 billing 一次决定；渠道清算账户主键由 billing 在头中给出，ledger 不读 reconciliation。</li>
 * <li>代码类字段以 String 承载稳定 Code，取值见对应枚举（如 {@code EconomicsType}、{@code AmountSource}）。</li>
 */
@Getter
@Setter
public class EconomicFactPayload {
    /** 经济事实主键（t_economic_fact.id），ledger 自动指令以它幂等。 */
    private Long factId;

    /** 经济事实类型代码，见 EconomicsType。 */
    private String factType;

    /** 发布经济事实事件的来源系统代码。 */
    private String sourceSystem;

    /** 来源系统内稳定且可幂等消费的事件键。 */
    private String sourceEventKey;

    /** 来源业务事实的单调递增版本。 */
    private Long sourceVersion;

    /** 来源事件规范化载荷的不可变摘要。 */
    private String sourcePayloadHash;

    /** 仅 PAYMENT：支付成功的 UTC 微秒时间，决定过账日期与生效规则版本。 */
    private Instant paymentSucceededTime;

    /** 订单所属 CCT Client 稳定业务 ID。 */
    private Long clientId;

    /** 订单冻结的 Operating Entity 编号（entityNo）。 */
    private String entityNo;

    /** 交易时点有效的 Local Partner；无合作方为空。 */
    private Long localPartnerId;

    /** Partner Agreement 与 OE 的关联 ID；无合作方为空。 */
    private Long partnerAgreementOeId;

    /** 平台服务费收取方 Finance Entity（ledger 主数据主键）；无合作方为空。 */
    private Long financeEntityId;

    /** 全部金额的 ISO 4217 币种。 */
    private String currency;

    /** 业务事实非负总金额。 */
    private BigDecimal grossAmount;

    /** billing 解析的 Finance 渠道清算账户主键（reconciliation t_channel_account.id）；规则引用 CHANNEL_ACCOUNT 角色时必填。 */
    private Long channelAccountId;

    /** 全部明细行，按行号升序。 */
    private List<EconomicFactLinePayload> lines;
}
