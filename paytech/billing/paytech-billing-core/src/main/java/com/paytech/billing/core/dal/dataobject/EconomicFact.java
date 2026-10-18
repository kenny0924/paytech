package com.paytech.billing.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.billing.api.enums.EconomicsType;
import com.paytech.billing.api.enums.EconomicFactStatus;

import lombok.Getter;
import lombok.Setter;

/**
 * t_economic_fact（原 t_transaction_economics）：经济事实头
 * <li>内容一经提交不可修改；只有登记状态、版本定稿、复核信息与发布时间随 Maker-Checker 和事件发布推进。</li>
 * <li>交易锚点（订单、上下文、税务快照、支付意图等）只对交易类事实必填；人工 / 成本调整登记为空。</li>
 */
@Getter
@Setter
public class EconomicFact {
    /**
     * 交易经济结果 Snowflake 主键
     */
    private Long id;

    /**
     * 经济事实类型（原 economics_type）
     */
    private EconomicsType factType;

    /**
     * 发布经济事实事件的来源系统代码
     */
    private String sourceSystem;

    /**
     * 来源系统内稳定且可幂等消费的事件键
     */
    private String sourceEventKey;

    /**
     * 来源业务事实的单调递增版本
     */
    private Long sourceVersion;

    /**
     * 来源事件规范化载荷摘要
     */
    private String sourcePayloadHash;

    /**
     * 派生类型的本库来源结果
     * <li>可为空。</li>
     */
    private Long parentEconomicsId;

    /**
     * Commerce 订单稳定业务ID
     * <li>交易类事实必填；人工 / 成本调整为空。</li>
     */
    private Long orderId;

    /**
     * Commerce Order Context 稳定业务ID
     * <li>交易类事实必填；人工 / 成本调整为空。</li>
     */
    private Long orderContextId;

    /**
     * 上下文格式
     * <li>允许值：TERM_PRICE、LEGACY_SCOPE。</li>
     * <li>交易类事实必填；人工 / 成本调整为空。</li>
     */
    private String contextFormat;

    /**
     * Payment 最终定价快照ID
     * <li>可为空。</li>
     */
    private Long paymentFactId;

    /**
     * 命中的 CCT Client Price ID
     * <li>可为空。</li>
     */
    private Long clientPriceId;

    /**
     * 冻结的结算工作日数
     * <li>可为空。</li>
     */
    private Integer settlementBusinessDays;

    /**
     * 工作日日历代码
     * <li>可为空。</li>
     */
    private String businessCalendarCode;

    /**
     * 结算时区
     * <li>可为空。</li>
     */
    private String settlementTimezone;

    /**
     * 当日截止秒数
     * <li>可为空。</li>
     */
    private Integer cutoffSecondOfDay;

    /**
     * Commerce 冻结的商业上下文 UTC 时间
     * <li>交易类事实必填；人工 / 成本调整为空。</li>
     */
    private Instant commercialContextTime;

    /**
     * 本库税务计算快照ID
     * <li>交易类事实必填；人工 / 成本调整为空。</li>
     */
    private Long taxSnapshotId;

    /**
     * Payment 支付意图ID
     * <li>交易类事实必填；人工 / 成本调整为空。</li>
     */
    private Long paymentIntentId;

    /**
     * Payment Attempt ID
     * <li>可为空。</li>
     */
    private Long paymentAttemptId;

    /**
     * 支付成功 UTC 时间
     * <li>仅 PAYMENT 非空。</li>
     */
    private Instant paymentSucceededTime;

    /**
     * Client 本金与 Partner 分润成熟 UTC 时间
     * <li>仅 PAYMENT 非空。</li>
     */
    private Instant balanceAvailableTime;

    /**
     * Commerce Refund Request ID
     * <li>可为空。</li>
     */
    private Long refundRequestId;

    /**
     * Payment Channel Refund ID
     * <li>可为空。</li>
     */
    private Long channelRefundId;

    /**
     * 本库重复扣款异常明细ID
     * <li>可为空。</li>
     */
    private Long duplicateCaptureItemId;

    /**
     * Payment Channel Dispute Notice ID
     * <li>可为空。</li>
     */
    private Long channelDisputeNoticeId;

    /**
     * Risk Dispute Case ID
     * <li>可为空。</li>
     */
    private Long disputeCaseId;

    /**
     * CCT Client ID
     */
    private Long clientId;

    /**
     * 交易 Operating Entity 编号
     */
    private String entityNo;

    /**
     * CCT Local Partner ID
     * <li>可为空；与 partnerAgreementOeId、financeEntityId 成组非空。</li>
     */
    private Long localPartnerId;

    /**
     * 消费者国家代码
     * <li>交易类事实必填；人工 / 成本调整为空。</li>
     */
    private String consumerCountryCode;

    /**
     * 实际支付方式代码
     * <li>交易类事实必填；人工 / 成本调整为空。</li>
     */
    private String paymentMethod;

    /**
     * 行业分类代码
     * <li>交易类事实必填；人工 / 成本调整为空。</li>
     */
    private String industryCode;

    /**
     * 四位 MCC
     * <li>交易类事实必填；人工 / 成本调整为空。</li>
     */
    private String mccCode;

    /**
     * CCT Client 合同ID
     * <li>交易类事实必填；人工 / 成本调整为空。</li>
     */
    private Long clientAgreementId;

    /**
     * CCT 合作方协议与 OE 的关联ID
     * <li>可为空。</li>
     */
    private Long partnerAgreementOeId;

    /**
     * 平台服务费收取方本库 Finance Entity ID
     * <li>可为空。</li>
     */
    private Long financeEntityId;

    /**
     * 币种
     */
    private String currency;

    /**
     * 非负总金额
     */
    private BigDecimal grossAmount;

    /**
     * 被本事实更正替代的前一经济事实；首次登记为空
     */
    private Long supersedesFactId;

    /**
     * 经济事实登记状态
     */
    private EconomicFactStatus status;

    /**
     * 事实版本
     * <li>DRAFT 为 0；批准时定稿为 1，替代事实为被替代事实版本 + 1；EconomicFactRecorded 的 aggregateVersion。</li>
     */
    private Long factVersion;

    /**
     * Finance 渠道清算账户（reconciliation t_channel_account.id）
     * <li>可为空；COST_ADJUSTMENT 必填。</li>
     */
    private Long channelAccountId;

    /**
     * 编制人平台用户标识
     * <li>人工 / 成本调整必填；系统生成的交易事实为空。</li>
     */
    private Long makerId;

    /**
     * 复核人平台用户标识
     * <li>批准后非空且不等于编制人。</li>
     */
    private Long checkerId;

    /**
     * 批准 UTC 微秒时间
     * <li>DRAFT 为空；事件 occurredAt。</li>
     */
    private Instant approvedTime;

    /**
     * EconomicFactRecorded 收到 Broker 确认后的 UTC 微秒时间
     * <li>未发布为空。</li>
     */
    private Instant publishedAt;

    /**
     * 该记录在 Billing 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;

    /**
     * 最后一次状态、版本或发布时间更新的 UTC 微秒时间
     */
    private Instant updateTime;
}
