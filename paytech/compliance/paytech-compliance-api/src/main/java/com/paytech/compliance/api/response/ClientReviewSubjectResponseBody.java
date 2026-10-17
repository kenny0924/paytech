package com.paytech.compliance.api.response;

import java.util.List;

/** 一个任务的受审主体及其材料。 */
public class ClientReviewSubjectResponseBody {

    /** 主体所属任务 ID。 */
    private String caseId;

    /** 该任务状态。 */
    private String caseStatus;

    /** CCT Party ID。 */
    private String partyId;

    /** CCT 来源资料 ID。 */
    private String profileId;

    /** CLIENT 或 RELATED_PERSON。 */
    private String roleType;

    /** 资格类型。 */
    private String qualificationType;

    /** 主任务对该子任务的依赖类型；主体本身为空。 */
    private List<String> dependencyTypes;

    /** 材料要求。 */
    private List<ClientReviewRequirementResponseBody> requirements;

    /** 读取主体所属任务 ID。 */
    public String getCaseId() {
        return caseId;
    }

    /** 设置主体所属任务 ID。 */
    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    /** 读取该任务状态。 */
    public String getCaseStatus() {
        return caseStatus;
    }

    /** 设置该任务状态。 */
    public void setCaseStatus(String caseStatus) {
        this.caseStatus = caseStatus;
    }

    /** 读取CCT Party ID。 */
    public String getPartyId() {
        return partyId;
    }

    /** 设置CCT Party ID。 */
    public void setPartyId(String partyId) {
        this.partyId = partyId;
    }

    /** 读取CCT 来源资料 ID。 */
    public String getProfileId() {
        return profileId;
    }

    /** 设置CCT 来源资料 ID。 */
    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    /** 读取CLIENT 或 RELATED_PERSON。 */
    public String getRoleType() {
        return roleType;
    }

    /** 设置CLIENT 或 RELATED_PERSON。 */
    public void setRoleType(String roleType) {
        this.roleType = roleType;
    }

    /** 读取资格类型。 */
    public String getQualificationType() {
        return qualificationType;
    }

    /** 设置资格类型。 */
    public void setQualificationType(String qualificationType) {
        this.qualificationType = qualificationType;
    }

    /** 读取主任务对该子任务的依赖类型；主体本身为空。 */
    public List<String> getDependencyTypes() {
        return dependencyTypes;
    }

    /** 设置主任务对该子任务的依赖类型；主体本身为空。 */
    public void setDependencyTypes(List<String> dependencyTypes) {
        this.dependencyTypes = dependencyTypes;
    }

    /** 读取材料要求。 */
    public List<ClientReviewRequirementResponseBody> getRequirements() {
        return requirements;
    }

    /** 设置材料要求。 */
    public void setRequirements(List<ClientReviewRequirementResponseBody> requirements) {
        this.requirements = requirements;
    }
}
