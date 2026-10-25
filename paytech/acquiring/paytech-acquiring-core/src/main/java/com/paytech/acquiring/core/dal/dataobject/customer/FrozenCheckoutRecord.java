package com.paytech.acquiring.core.dal.dataobject.customer;

import lombok.*;
import lombok.experimental.Accessors;

import java.time.Instant;

/** 同一订单的上下文、法律事实与合同查询投影，仅在本模块使用。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public class FrozenCheckoutRecord {
    /** 订单上下文主键。 */
    private long orderContextId;

    /** 原订单主键。 */
    private long orderId;

    /** 商业事实解析时刻。 */
    private Instant commercialContextTime;

    /** Client 与 OE 的关联。 */
    private long affiliationId;

    /** 命中的 OE 范围。 */
    private long entityScopeId;

    /** 经营主体来源关联。 */
    private long entitySourceRelationId;

    /** 客户定价版本。 */
    private long clientPricingVersionId;

    /** 合作方分成版本。 */
    private Long partnerShareVersionId;

    /** 平台收费版本。 */
    private Long platformChargeVersionId;

    /** 平台收费法律 Party。 */
    private Long platformServicePartyId;

    /** 风险评估引用。 */
    private long riskCheckoutAssessmentId;

    /** 税务快照引用。 */
    private long financeTaxSnapshotId;

    /** 税务快照摘要。 */
    private String financeTaxSnapshotHash;

    /** 计税国家。 */
    private String consumerCountryCode;

    /** 文档语言。 */
    private String locale;

    /**
     * 商业关系快照的资金流向 Code
     * <li>固定 Mock 解析或 commercial 未返回快照时为空。</li>
     */
    private String fundFlow;

    /**
     * 商业关系生效模型版本
     * <li>0 只记录、下游沿用旧字段；1 下游按快照生效；无快照时为 0。</li>
     */
    private int effectiveRelationshipModelVersion;

    /**
     * 商业关系快照的推导规则版本
     * <li>无快照时为空。</li>
     */
    private Integer snapshotModelVersion;

    /** 订单法律事实主键。 */
    private long orderLegalId;

    /** 客户协议。 */
    private long clientAgreementId;

    /** 合作方协议。 */
    private Long partnerAgreementId;

    /** 协议经营主体关联。 */
    private Long partnerAgreementOperatingEntityId;

    /** 消费者条款文档。 */
    private long consumerTermsDocumentId;

    /** 消费者条款源文件。 */
    private long consumerTermsFileId;

    /** 消费者条款摘要。 */
    private String consumerTermsContentHash;

    /** 退款政策文档。 */
    private long refundPolicyDocumentId;

    /** 退款政策文件。 */
    private long refundPolicyFileId;

    /** 退款政策摘要。 */
    private String refundPolicyContentHash;

    /** 隐私声明文档。 */
    private long privacyNoticeDocumentId;

    /** 隐私声明文件。 */
    private long privacyNoticeFileId;

    /** 隐私声明摘要。 */
    private String privacyNoticeContentHash;

    /** 卖方披露身份。 */
    private long sellerDisclosureId;

    /** 卖方公开法定名称。 */
    private String publicLegalName;

    /** 卖方注册号。 */
    private String publicRegistrationNumber;

    /** 卖方税号。 */
    private String publicTaxRegistrationNumber;

    /** 卖方公开地址。 */
    private String publicAddressText;

    /** 卖方支持邮箱。 */
    private String publicSupportEmail;

    /** 卖方支持电话。 */
    private String publicSupportPhone;

    /** 消费者合同主键。 */
    private long consumerContractId;

    /** 订单合同文件。 */
    private long renderedFileId;

    /** 订单合同摘要。 */
    private String renderedContentHash;

    /** 原合同呈现时刻。 */
    private Instant presentedAt;
}
