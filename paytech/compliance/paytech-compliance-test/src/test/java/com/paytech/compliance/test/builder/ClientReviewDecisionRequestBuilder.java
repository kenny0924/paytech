package com.paytech.risk.test.builder.compliance;

import com.paytech.risk.api.compliance.request.ClientReviewDecisionRequestBody;

/**
 * O08 通过 / O09 最终拒绝的请求 Builder
 * <li>template() 给出合法原因代码、内部理由与客户可见说明；build() 不修复刻意设置的非法值。</li>
 *
 * @author Kenny
 */
public final class ClientReviewDecisionRequestBuilder {
    public static final String REASON_CODE = "DOCUMENTS_VERIFIED";

    public static final String REASON = "Synthetic internal review reason";

    public static final String PUBLIC_NOTE = "Your onboarding review is complete.";

    private final ClientReviewDecisionRequestBody body = new ClientReviewDecisionRequestBody();

    private ClientReviewDecisionRequestBuilder() {
        body.setReasonCode(REASON_CODE);
        body.setReason(REASON);
        body.setPublicNote(PUBLIC_NOTE);
    }

    /** 合法的结论请求。 */
    public static ClientReviewDecisionRequestBuilder template() {
        return new ClientReviewDecisionRequestBuilder();
    }

    public ClientReviewDecisionRequestBuilder reasonCode(String value) {
        body.setReasonCode(value);
        return this;
    }

    public ClientReviewDecisionRequestBuilder reason(String value) {
        body.setReason(value);
        return this;
    }

    public ClientReviewDecisionRequestBuilder publicNote(String value) {
        body.setPublicNote(value);
        return this;
    }

    public ClientReviewDecisionRequestBody build() {
        return body;
    }
}
