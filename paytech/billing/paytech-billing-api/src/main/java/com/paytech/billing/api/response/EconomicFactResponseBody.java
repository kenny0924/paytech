package com.paytech.billing.api.response;

import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 经济事实详情：头、登记与批准信息、发布状态和全部明细行；ID、版本与金额均为十进制字符串。 */
@Getter
@Setter
public class EconomicFactResponseBody {
    /** 经济事实 ID */
    private String factId;

    /** 经济事实类型代码（EconomicsType） */
    private String factType;

    /** 登记状态：DRAFT、APPROVED、SUPERSEDED */
    private String status;

    /**
     * 事实版本
     * <li>DRAFT 为 0；批准时定稿为 1 或被替代事实版本 + 1。</li>
     */
    private String factVersion;

    /** 来源系统代码 */
    private String sourceSystem;

    /** 来源引用（source_event_key） */
    private String sourceReference;

    /** 提交内容摘要（source_payload_hash，SHA-256 小写十六进制） */
    private String contentHash;

    /** 来源事实；为空表示无 */
    private String parentFactId;

    /** 被替代事实；为空表示首次登记 */
    private String supersedesFactId;

    /** CCT Client ID */
    private String clientId;

    /** Operating Entity 编号 */
    private String entityNo;

    /** Local Partner；可为空 */
    private String localPartnerId;

    /** Partner Agreement 与 OE 的关联 ID；可为空 */
    private String partnerAgreementOeId;

    /** 平台服务费收取方 Finance Entity；可为空 */
    private String financeEntityId;

    /** 币种 */
    private String currency;

    /** 明细行金额之和，按币种精度补齐 */
    private String grossAmount;

    /** Finance 渠道清算账户；可为空 */
    private String channelAccountId;

    /** 编制人；系统生成的交易事实为空 */
    private String makerId;

    /** 复核人；未批准为空 */
    private String checkerId;

    /** 批准时间（UTC）；未批准为空 */
    private Instant approvedTime;

    /**
     * 支付成功时间（UTC）
     * <li>仅 PAYMENT，其余为空。</li>
     */
    private Instant paymentSucceededTime;

    /** EconomicFactRecorded 发布确认时间（UTC）；未发布为空 */
    private Instant publishedAt;

    /** 全部明细行，按行号升序 */
    private List<EconomicFactLineResponseBody> lines;
}
