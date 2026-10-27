package com.paytech.compliance.api.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 一个案件的受审主体及其材料、检查与筛查。 */
@Getter
@Setter
public class ClientReviewSubjectResponseBody {

    /** 主体所属案件 ID。 */
    private String caseId;

    /** 该案件状态。 */
    private String caseStatus;

    /** Party ID。 */
    private String partyId;

    /** 来源资料 ID。 */
    private String profileId;

    /** CLIENT 或 RELATED_PERSON。 */
    private String roleType;

    /** 资格类型。 */
    private String qualificationType;

    /** 关联人员的 UBO 认定依据；主体本身为空。 */
    private String uboBasis;

    /** 关联人员的职务；主体本身为空。 */
    private String jobTitle;

    /** 主案件对该子案件的依赖类型；主体本身为空。 */
    private List<String> dependencyTypes;

    /** 材料要求。 */
    private List<ClientReviewRequirementResponseBody> requirements;

    /** 检查项。 */
    private List<ClientReviewCheckResponseBody> checks;

    /** 筛查执行（每个筛查类型的全部执行，最新在前）。 */
    private List<ClientReviewScreeningRunResponseBody> screenings;
}
