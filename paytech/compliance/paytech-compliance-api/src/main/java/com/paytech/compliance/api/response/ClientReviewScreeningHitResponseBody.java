package com.paytech.compliance.api.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 一条筛查命中
 * <li>不保存命中实体姓名：审核员按 externalHitRef 在提供方查看原文。</li>
 */
@Getter
@Setter
public class ClientReviewScreeningHitResponseBody {

    /** 命中 ID。 */
    private String hitId;

    /** 提供方命中引用。 */
    private String externalHitRef;

    /** 实体类型：PERSON、ORGANIZATION、VESSEL 或 OTHER。 */
    private String entityType;

    /** 匹配方式代码。 */
    private String matchType;

    /** 匹配分数十进制字符串。 */
    private String matchScore;

    /** 风险等级：LOW、MEDIUM、HIGH 或 UNKNOWN。 */
    private String riskLevel;

    /** 命中名单类型，例如 SANCTIONS、PEP、ADVERSE_MEDIA。 */
    private List<String> listTypes;

    /** OPEN、RESOLVED 或 SUPERSEDED。 */
    private String status;

    /** 处置记录，按版本升序。 */
    private List<ClientReviewHitResolutionResponseBody> resolutions;
}
