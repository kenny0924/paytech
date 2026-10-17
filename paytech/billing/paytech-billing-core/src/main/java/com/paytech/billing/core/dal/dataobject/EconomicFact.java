package com.paytech.billing.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.billing.api.enums.EconomicsType;
import com.paytech.billing.core.enums.EconomicFactStatus;

import lombok.Getter;
import lombok.Setter;

/** t_economic_fact（原 t_transaction_economics）：按业务事实冻结的不可变经济事实；本期只读，登记入口在第 11a 步实现。 */
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
     */
    private Long orderId;

    /**
     * Commerce Order Context 稳定业务ID
     */
    private Long orderContextId;

    /**
     * 上下文格式
     * <li>允许值：TERM_PRICE、LEGACY_SCOPE。</li>
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
     */
    private Instant commercialContextTime;

    /**
     * 本库税务计算快照ID
     */
    private Long taxSnapshotId;

    /**
     * Payment 支付意图ID
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
     */
    private String consumerCountryCode;

    /**
     * 实际支付方式代码
     */
    private String paymentMethod;

    /**
     * 行业分类代码
     */
    private String industryCode;

    /**
     * 四位 MCC
     */
    private String mccCode;

    /**
     * CCT Client 合同ID
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
     * 该记录在 Billing 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;
}
