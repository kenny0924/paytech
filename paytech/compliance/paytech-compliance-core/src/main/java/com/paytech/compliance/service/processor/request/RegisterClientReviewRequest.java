package com.paytech.risk.compliance.service.processor.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * I01 的 Owner 输入：只保留建任务需要的稳定标识，不承载主体个人资料原值。
 * <li>资料快照本期不做（2026-10-13 用户决定），入口不把姓名、证件号、地址传入本模块。</li>
 */
@Getter
@AllArgsConstructor
public class RegisterClientReviewRequest {
    /** 来源提交编号（UUID）。 */
    @NotBlank
    private final String sourceSubmissionId;
    /** CCT 申请 ID。 */
    @Positive
    private final long applicationId;
    /** CCT Client ID。 */
    @Positive
    private final long clientId;
    /** 客户类型：INDIVIDUAL 或 ORGANIZATION。 */
    @NotBlank
    private final String clientType;
    /** 提交 UTC 时间。 */
    @NotNull
    private final Instant submittedAt;
    /** 受审主体，第一项为主主体。 */
    @NotEmpty
    @Valid
    private final List<Subject> subjects;
    /** 组织与关联人员的关系。 */
    @NotNull
    @Valid
    private final List<Relation> relations;
    /** 材料引用。 */
    @NotNull
    @Valid
    private final List<Document> documents;

    /** 受审主体。 */
    @Getter
    @AllArgsConstructor
    public static class Subject {
        /** Party ID。 */
        @Positive
        private final long partyId;
        /** 单份资料 ID。 */
        @Positive
        private final long profileId;
        /** PRIMARY 或 RELATED_PERSON。 */
        @NotBlank
        private final String subjectRole;
        /** 资格类型代码。 */
        @NotBlank
        private final String qualificationType;
    }

    /** 关系。 */
    @Getter
    @AllArgsConstructor
    public static class Relation {
        /** 关系 ID。 */
        @Positive
        private final long relationId;
        /** 关联人员 Party ID。 */
        @Positive
        private final long relatedPartyId;
        /** 关系类型。 */
        @NotBlank
        private final String relationType;
        /** 持股比例，可空。 */
        private final BigDecimal ownershipPercentage;
    }

    /** 材料引用。 */
    @Getter
    @AllArgsConstructor
    public static class Document {
        /** CCT 材料引用 ID。 */
        @Positive
        private final long documentId;
        /** 所属主体 Party ID。 */
        @Positive
        private final long subjectPartyId;
        /** 材料要求代码。 */
        @NotBlank
        private final String requirementCode;
        /** 材料类型代码。 */
        @NotBlank
        private final String documentType;
        /** Platform 文件 ID。 */
        @Positive
        private final long fileId;
        /** 文件 SHA-256。 */
        @NotBlank
        private final String contentHash;
    }
}
