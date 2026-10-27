package com.paytech.compliance.api.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** CP-1 解析出的生效尽调程序版本及其模板。 */
@Getter
@Setter
public class ProgramResolutionResponseBody {

    /** 程序 ID，十进制字符串。 */
    private String programId;

    /** 程序编码。 */
    private String programCode;

    /** 生效程序版本 ID，十进制字符串；I01 立案时冻结该版本。 */
    private String programVersionId;

    /** 程序版本号。 */
    private Integer versionNo;

    /** 资格类型：INDIVIDUAL_KYC、ORGANIZATION_KYB 或 RELATED_PERSON_KYC。 */
    private String qualificationType;

    /**
     * 命中程序的辖区代码
     * <li>为空表示命中的是全局程序。</li>
     */
    private String jurisdictionCode;

    /** 尽调档次：SIMPLIFIED、STANDARD 或 ENHANCED。 */
    private String tier;

    /**
     * 程序 Owner 的 Local Partner ID
     * <li>为空表示平台标准程序。</li>
     */
    private String ownerPartnerId;

    /** 批准后的复审间隔月数；未配置为空。 */
    private Integer reviewIntervalMonths;

    /** 是否要求推荐人与决定人不同（四眼）。 */
    private Boolean fourEyesRequired;

    /** 材料槽。 */
    private List<ProgramMaterialBody> materials;

    /** 检查项（本期只有人工检查）。 */
    private List<ProgramCheckBody> checks;

    /** 名单筛查项。 */
    private List<ProgramScreeningBody> screenings;
}
