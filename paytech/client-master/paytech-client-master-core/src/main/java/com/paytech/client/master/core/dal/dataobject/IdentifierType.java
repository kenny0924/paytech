package com.paytech.client.master.core.dal.dataobject;

import com.paytech.client.master.domain.enums.CatalogStatus;
import com.paytech.client.master.domain.enums.ClientType;
import com.paytech.client.master.domain.enums.IssuerScopeMode;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 法律标识类型目录行（t_identifier_type）。 */
@Getter
@Setter
public class IdentifierType {
    /** 目录行 ID。 */
    private Long identifierTypeId;
    /** 标识类型代码，全局唯一。 */
    private String identifierTypeCode;
    /** 所属辖区；为空表示全局类型。 */
    private String jurisdictionCode;
    /** 适用主体类型。 */
    private ClientType partyType;
    /** 展示名称文案键。 */
    private String labelKey;
    /** 规范化后号码须完整匹配的正则；为空只做通用字符校验。 */
    private String formatRegex;
    /** 规范化规则代码。 */
    private String normalizationRule;
    /** 校验位算法代码。 */
    private String checksumRule;
    /** 唯一性作用域方式。 */
    private IssuerScopeMode issuerScopeMode;
    /** 后续外部核验的标准 API 代码，可空。 */
    private String verificationCheckCode;
    /** 状态。 */
    private CatalogStatus status;
    /** 创建 UTC 时间。 */
    private Instant createTime;
    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
