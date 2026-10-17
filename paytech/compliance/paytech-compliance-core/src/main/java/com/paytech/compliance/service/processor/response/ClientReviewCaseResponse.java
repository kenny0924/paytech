package com.paytech.compliance.service.processor.response;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Client 入驻审核任务的 Owner 结果；列表只填摘要字段，详情填全部。 */
@Getter
@AllArgsConstructor
public class ClientReviewCaseResponse {
    /** 主案件 ID。 */
    private final long caseId;
    /** 案件编号。 */
    private final String caseNo;
    /** CCT 申请 ID。 */
    private final long applicationId;
    /** CCT Client ID。 */
    private final long clientId;
    /** 来源提交编号。 */
    private final String sourceSubmissionId;
    /** 资格类型。 */
    private final String qualificationType;
    /** 主案件状态。 */
    private final String status;
    /** 主案件并发版本。 */
    private final long version;
    /** 提交时间。 */
    private final Instant submittedAt;
    /** 结束时间；进行中为空。 */
    private final Instant decidedAt;
    /** 客户可见说明。 */
    private final String publicNote;
    /** 退回分区。 */
    private final String returnSection;
    /** 主体与材料；列表为空列表。 */
    private final List<Subject> subjects;
    /** 已接收分类；列表为空列表。 */
    private final List<Classification> classifications;
    /** 通过门禁未满足项；列表为空列表。 */
    private final List<String> approvalBlockers;

    /** 一个案件的 PRIMARY 主体及其材料。 */
    @Getter
    @AllArgsConstructor
    public static class Subject {
        /** 主体所属案件 ID。 */
        private final long caseId;
        /** 该案件状态。 */
        private final String caseStatus;
        /** Party ID。 */
        private final long partyId;
        /** 来源资料 ID。 */
        private final long profileId;
        /** CLIENT 或 RELATED_PERSON。 */
        private final String roleType;
        /** 资格类型。 */
        private final String qualificationType;
        /** 主案件对该子案件的依赖类型；主体本身为空列表。 */
        private final List<String> dependencyTypes;
        /** 材料要求。 */
        private final List<Requirement> requirements;
    }

    /** 材料要求。 */
    @Getter
    @AllArgsConstructor
    public static class Requirement {
        /** 案件材料要求 ID。 */
        private final long requirementId;
        /** 要求代码。 */
        private final String requirementCode;
        /** 材料类型。 */
        private final String documentType;
        /** 是否基础必需。 */
        private final boolean required;
        /** 要求状态。 */
        private final String status;
        /** 证据。 */
        private final List<Evidence> evidence;
    }

    /** 证据引用。 */
    @Getter
    @AllArgsConstructor
    public static class Evidence {
        /** 证据 ID。 */
        private final long evidenceId;
        /** Platform 文件 ID。 */
        private final long fileId;
        /** 证据状态。 */
        private final String status;
    }

    /** 分类。 */
    @Getter
    @AllArgsConstructor
    public static class Classification {
        /** 行业代码。 */
        private final String industryCode;
        /** 四位 MCC。 */
        private final String mccCode;
        /** 是否主要分类。 */
        private final boolean primary;
    }
}
