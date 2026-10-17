package com.paytech.risk.test.builder.compliance;

import java.util.ArrayList;

import com.paytech.risk.api.compliance.request.ClientReviewClassificationBody;
import com.paytech.risk.api.compliance.request.ClientReviewClassificationsRequestBody;

/**
 * I02 分类交付的请求 Builder
 * <li>template() 为给定申请与提交编号的一个主要分类 RETAIL/5411，确认人与确认时间固定；build() 不修复刻意设置的非法值。</li>
 *
 * @author Kenny
 */
public final class ClientReviewClassificationsRequestBuilder {
    public static final String CONFIRMED_BY = "930000001";

    public static final String CONFIRMED_AT = "2026-10-13T09:00:00.123456Z";

    private final ClientReviewClassificationsRequestBody body = new ClientReviewClassificationsRequestBody();

    private ClientReviewClassificationsRequestBuilder(long applicationId, String submissionId) {
        body.setApplicationId(Long.toString(applicationId));
        body.setSubmissionId(submissionId);
        body.setConfirmedBy(CONFIRMED_BY);
        body.setConfirmedAt(CONFIRMED_AT);
        body.setClassifications(new ArrayList<>());
    }

    /** 指定任务的申请与提交编号，含一个主要分类。 */
    public static ClientReviewClassificationsRequestBuilder template(long applicationId, String submissionId) {
        return new ClientReviewClassificationsRequestBuilder(applicationId, submissionId).add("RETAIL", "5411", true);
    }

    /** 清空分类集合。 */
    public ClientReviewClassificationsRequestBuilder clear() {
        body.getClassifications().clear();
        return this;
    }

    /** 追加一条分类；primary 可为 null 构造缺失负例。 */
    public ClientReviewClassificationsRequestBuilder add(String industryCode, String mccCode, Boolean primary) {
        var item = new ClientReviewClassificationBody();
        item.setIndustryCode(industryCode);
        item.setMccCode(mccCode);
        item.setPrimary(primary);
        body.getClassifications().add(item);
        return this;
    }

    public ClientReviewClassificationsRequestBuilder applicationId(String value) {
        body.setApplicationId(value);
        return this;
    }

    public ClientReviewClassificationsRequestBuilder submissionId(String value) {
        body.setSubmissionId(value);
        return this;
    }

    public ClientReviewClassificationsRequestBuilder confirmedBy(String value) {
        body.setConfirmedBy(value);
        return this;
    }

    public ClientReviewClassificationsRequestBuilder confirmedAt(String value) {
        body.setConfirmedAt(value);
        return this;
    }

    /** 置空分类集合字段本身。 */
    public ClientReviewClassificationsRequestBuilder nullClassifications() {
        body.setClassifications(null);
        return this;
    }

    public ClientReviewClassificationsRequestBody build() {
        return body;
    }
}
