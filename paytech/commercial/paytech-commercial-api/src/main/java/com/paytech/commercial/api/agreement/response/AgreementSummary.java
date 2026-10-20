package com.paytech.commercial.api.agreement.response;

import lombok.Getter;
import lombok.Setter;

/** 合同列表中的一行：合同主表字段与 BCR Root 当前版本。 */
@Getter
@Setter
public class AgreementSummary {
    /** 合同 ID */
    private String agreementId;

    /** 合同编号 */
    private String agreementNo;

    /** 合同名称 */
    private String agreementName;

    /**
     * 合同类型
     * <li>CLIENT、PARTNER。</li>
     */
    private String agreementType;

    /**
     * 合同状态
     * <li>DRAFT、PENDING_SIGNATURE、SIGNED、ACTIVE、EXPIRED、TERMINATED、CANCELLED。</li>
     */
    private String status;

    /**
     * 业务模式
     * <li>MOR、PSP；PARTNER 合同为空。</li>
     */
    private String businessMode;

    /**
     * 资金模式
     * <li>PARTNER 合同为空。</li>
     */
    private String fundMode;

    /**
     * Client ID
     * <li>CLIENT 合同非空，PARTNER 合同为空。</li>
     */
    private String clientId;

    /**
     * Local Partner ID
     * <li>PARTNER 合同非空，CLIENT 合同为空。</li>
     */
    private String localPartnerId;

    /**
     * CLIENT 合同的 OE 编号
     * <li>PARTNER 合同为空，其覆盖 OE 见合同详情。</li>
     */
    private String entityNo;

    /** 生效起点，UTC ISO 8601 */
    private String effectiveFrom;

    /**
     * 生效终点（不含），UTC ISO 8601
     * <li>为空表示无固定终点。</li>
     */
    private String effectiveTo;

    /** 签署完成时刻，UTC ISO 8601，未签完为空 */
    private String signedTime;

    /** 激活时刻，UTC ISO 8601，未激活为空 */
    private String activatedTime;

    /** 合同 BCR Root 当前版本，下一次写入的 expectedChangeVersion */
    private Long changeVersion;

    /** 合同行最近更新时间，UTC ISO 8601 */
    private String updateTime;
}
