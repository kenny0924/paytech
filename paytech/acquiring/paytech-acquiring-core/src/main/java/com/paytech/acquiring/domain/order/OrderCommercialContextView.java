package com.paytech.acquiring.domain.order;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/**
 * 订单、冻结上下文与法律事实三表组合的商业字段，供内部订单上下文查询使用
 * <li>以订单为主表左连接：上下文或法律事实缺失时对应主键为空，由查询方区分不存在与数据损坏。</li>
 * <li>不读取消费者文档、法律披露、公开卖家信息、风控引用或税务摘要。</li>
 */
@Getter
@Setter
public class OrderCommercialContextView {
    /** 订单主键。 */
    private long orderId;

    /** 订单所属 Client 主键。 */
    private long clientId;

    /** 支付准备冻结的 OE 编号。 */
    private String entityNo;

    /** 冻结的 OE 来源类型。 */
    private String entitySourceType;

    /** PARTNER 来源所属合作方，其他来源为空。 */
    private Long localPartnerId;

    /** 冻结的平台行业目录代码。 */
    private String industryCode;

    /** 冻结的四位 MCC。 */
    private String mccCode;

    /** 支付准备确定的最终应付金额，以订单币种主单位表示。 */
    private BigDecimal totalAmount;

    /** 订单币种。 */
    private String currency;

    /** 冻结上下文主键；订单尚未完成支付准备时为空。 */
    private Long orderContextId;

    /** CCT 解析商业条款时使用的冻结时点。 */
    private Instant commercialContextTime;

    /** 冻结的 Client 定价条款版本。 */
    private Long clientPricingVersionId;

    /** PARTNER 来源冻结的分润条款版本，其他来源为空。 */
    private Long partnerShareVersionId;

    /** PARTNER 来源冻结的平台服务费条款版本，其他来源为空。 */
    private Long platformChargeVersionId;

    /** PARTNER 来源冻结的平台签约法律 Party，其他来源为空。 */
    private Long platformServicePartyId;

    /** 历史订单的旧 Finance 税务快照引用；2026-10-29 起新订单为空，税务字段改由 t_order_tax_calculation 提供。 */
    private Long financeTaxSnapshotId;

    /** 冻结的消费者国家。 */
    private String consumerCountryCode;

    /** 法律事实主键；上下文存在而法律事实缺失时为空，属于数据损坏。 */
    private Long orderLegalId;

    /** 冻结的 Client/OE 合同。 */
    private Long clientAgreementId;

    /** PARTNER 来源冻结的 Partner 合同，其他来源为空。 */
    private Long partnerAgreementId;

    /** PARTNER 来源冻结的 Partner 合同与 OE 关联，其他来源为空。 */
    private Long partnerAgreementOperatingEntityId;

    /** 订单已冻结商业上下文时返回 true；上下文缺失表示尚未完成支付准备。 */
    public boolean hasContext() {
        return orderContextId != null;
    }

    /** 上下文、法律事实、OE 与最终金额在支付准备时同事务写入；已冻结但任一缺失属于数据损坏。 */
    public boolean isComplete() {
        return orderLegalId != null && entityNo != null && totalAmount != null;
    }
}
