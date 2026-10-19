package com.paytech.commercial.api.agreement.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 合同详情：主档、参与方、文件、覆盖 OE、订阅产品与开通项。 */
@Getter
@Setter
public class AgreementDetailResponseBody {
    /** 合同 ID */
    private String agreementId;

    /** 合同编号 */
    private String agreementNo;

    /** 合同名称 */
    private String agreementName;

    /** 合同类型：CLIENT、PARTNER */
    private String agreementType;

    /** 业务模式；PARTNER 为空 */
    private String businessMode;

    /** 资金模式；PARTNER 为空 */
    private String fundMode;

    /** 定价权归属；PARTNER 为空 */
    private String pricingAuthority;

    /** 推荐伙伴；可空 */
    private String referralPartnerId;

    /** Client ID；PARTNER 为空 */
    private String clientId;

    /** Local Partner ID；CLIENT 为空 */
    private String localPartnerId;

    /** CLIENT 合同 OE；PARTNER 为空 */
    private String entityNo;

    /** PARTNER 合同覆盖 OE，升序；CLIENT 为空列表 */
    private List<String> operatingEntityNos;

    /** 合同状态 */
    private String status;

    /** 生效起点（含），UTC ISO 8601 */
    private String effectiveFrom;

    /** 生效终点（不含），可空 */
    private String effectiveTo;

    /** 签署完成时刻，可空 */
    private String signedTime;

    /** 激活时刻，可空 */
    private String activatedTime;

    /** 合同 BCR Root 当前版本，下一次写入的 expectedChangeVersion */
    private Long changeVersion;

    /** 参与方角色，按 ID 升序 */
    private List<AgreementPartyDetail> parties;

    /** 合同文件，按 ID 升序 */
    private List<AgreementDocumentDetail> documents;

    /** 订阅的产品代码，升序（不含基座产品） */
    private List<String> productCodes;

    /** 开通项，按 ID 升序 */
    private List<AgreementCapabilityDetail> capabilities;
}
