package com.paytech.risk.api.compliance.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** I02：CCT 确认的同一任务固定分类集合。 */
public class ClientReviewClassificationsRequestBody {

    /** CCT 申请 ID。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String applicationId;

    /** 来源提交编号。 */
    @NotBlank
    @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    private String submissionId;

    /** 确认人 Account ID。 */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String confirmedBy;

    /** 确认时间，UTC ISO-8601。 */
    @NotBlank
    @Size(max = 40)
    private String confirmedAt;

    /** 分类集合，1—20 条。 */
    @NotEmpty
    @Size(max = 20)
    private List<@Valid ClientReviewClassificationBody> classifications;

    /** 读取CCT 申请 ID。 */
    public String getApplicationId() {
        return applicationId;
    }

    /** 设置CCT 申请 ID。 */
    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    /** 读取来源提交编号。 */
    public String getSubmissionId() {
        return submissionId;
    }

    /** 设置来源提交编号。 */
    public void setSubmissionId(String submissionId) {
        this.submissionId = submissionId;
    }

    /** 读取确认人 Account ID。 */
    public String getConfirmedBy() {
        return confirmedBy;
    }

    /** 设置确认人 Account ID。 */
    public void setConfirmedBy(String confirmedBy) {
        this.confirmedBy = confirmedBy;
    }

    /** 读取确认时间，UTC ISO-8601。 */
    public String getConfirmedAt() {
        return confirmedAt;
    }

    /** 设置确认时间，UTC ISO-8601。 */
    public void setConfirmedAt(String confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    /** 读取分类集合，1—20 条。 */
    public List<ClientReviewClassificationBody> getClassifications() {
        return classifications;
    }

    /** 设置分类集合，1—20 条。 */
    public void setClassifications(List<ClientReviewClassificationBody> classifications) {
        this.classifications = classifications;
    }
}
