package com.paytech.risk.api.compliance.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** CCT 正式提交后登记 Risk 审核任务的完整受审事实；按来源提交编号防重。 */
public class ClientReviewRegistrationRequestBody {

    /**
     * 来源提交编号
     * <li>CCT 正式提交时生成的 UUID；同一提交的重试复用，退回重提更换。</li>
     * <li>是业务防重身份，不是 HTTP Idempotency-Key。</li>
     */
    @NotBlank
    @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    private String sourceSubmissionId;

    /** CCT Client 申请 ID，正 BIGINT 十进制字符串。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String applicationId;

    /** CCT 申请编号。 */
    @NotBlank
    @Size(max = 64)
    private String applicationNo;

    /** CCT Client ID，正 BIGINT 十进制字符串。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String clientId;

    /**
     * 客户类型
     * <li>INDIVIDUAL 或 ORGANIZATION。</li>
     */
    @NotBlank
    @Pattern(regexp = "^(INDIVIDUAL|ORGANIZATION)$")
    private String clientType;

    /** 发起申请的 Platform CUSTOMER Account ID，正 BIGINT 十进制字符串。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String requestedByAccountId;

    /**
     * 提交时间
     * <li>UTC 六位微秒 ISO-8601 字符串。</li>
     */
    @NotBlank
    @Size(max = 32)
    private String submittedAt;

    /** 经营资料。 */
    @NotNull
    @Valid
    private ClientReviewBusinessBody business;

    /**
     * 受审主体
     * <li>第一项为主主体，其余为提交时仍在范围内的关联人员；最多 101 项。</li>
     */
    @NotEmpty
    @Size(max = 101)
    @Valid
    private List<ClientReviewSubjectBody> subjects;

    /** 组织与关联人员的关系；个人申请为空列表。 */
    @NotNull
    @Size(max = 500)
    @Valid
    private List<ClientReviewRelationBody> relations;

    /** 材料引用；文件字节由 contentHash 固定。 */
    @NotNull
    @Size(max = 500)
    @Valid
    private List<ClientReviewDocumentBody> documents;

    /** 读取来源提交编号。 */
    public String getSourceSubmissionId() {
        return sourceSubmissionId;
    }

    /** 设置来源提交编号。 */
    public void setSourceSubmissionId(String sourceSubmissionId) {
        this.sourceSubmissionId = sourceSubmissionId;
    }

    /** 读取CCT Client 申请 ID，正 BIGINT 十进制字符串。 */
    public String getApplicationId() {
        return applicationId;
    }

    /** 设置CCT Client 申请 ID，正 BIGINT 十进制字符串。 */
    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    /** 读取CCT 申请编号。 */
    public String getApplicationNo() {
        return applicationNo;
    }

    /** 设置CCT 申请编号。 */
    public void setApplicationNo(String applicationNo) {
        this.applicationNo = applicationNo;
    }

    /** 读取CCT Client ID，正 BIGINT 十进制字符串。 */
    public String getClientId() {
        return clientId;
    }

    /** 设置CCT Client ID，正 BIGINT 十进制字符串。 */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /** 读取客户类型。 */
    public String getClientType() {
        return clientType;
    }

    /** 设置客户类型。 */
    public void setClientType(String clientType) {
        this.clientType = clientType;
    }

    /** 读取发起申请的 Platform CUSTOMER Account ID，正 BIGINT 十进制字符串。 */
    public String getRequestedByAccountId() {
        return requestedByAccountId;
    }

    /** 设置发起申请的 Platform CUSTOMER Account ID，正 BIGINT 十进制字符串。 */
    public void setRequestedByAccountId(String requestedByAccountId) {
        this.requestedByAccountId = requestedByAccountId;
    }

    /** 读取提交时间。 */
    public String getSubmittedAt() {
        return submittedAt;
    }

    /** 设置提交时间。 */
    public void setSubmittedAt(String submittedAt) {
        this.submittedAt = submittedAt;
    }

    /** 读取经营资料。 */
    public ClientReviewBusinessBody getBusiness() {
        return business;
    }

    /** 设置经营资料。 */
    public void setBusiness(ClientReviewBusinessBody business) {
        this.business = business;
    }

    /** 读取受审主体。 */
    public List<ClientReviewSubjectBody> getSubjects() {
        return subjects;
    }

    /** 设置受审主体。 */
    public void setSubjects(List<ClientReviewSubjectBody> subjects) {
        this.subjects = subjects;
    }

    /** 读取组织与关联人员的关系；个人申请为空列表。 */
    public List<ClientReviewRelationBody> getRelations() {
        return relations;
    }

    /** 设置组织与关联人员的关系；个人申请为空列表。 */
    public void setRelations(List<ClientReviewRelationBody> relations) {
        this.relations = relations;
    }

    /** 读取材料引用；文件字节由 contentHash 固定。 */
    public List<ClientReviewDocumentBody> getDocuments() {
        return documents;
    }

    /** 设置材料引用；文件字节由 contentHash 固定。 */
    public void setDocuments(List<ClientReviewDocumentBody> documents) {
        this.documents = documents;
    }
}
