package com.paytech.risk.test.builder.compliance;

import com.paytech.risk.api.compliance.request.ClientReviewReturnRequestBody;

/**
 * O07 退回修改的请求 Builder
 * <li>template() 给出合法原因代码、退回分区与客户可见说明；build() 不修复刻意设置的非法值。</li>
 *
 * @author Kenny
 */
public final class ClientReviewReturnRequestBuilder {
    public static final String REASON_CODE = "DOCUMENT_UNREADABLE";

    public static final String SECTION = "MATERIALS";

    public static final String PUBLIC_NOTE = "Please upload a clearer registration certificate.";

    private final ClientReviewReturnRequestBody body = new ClientReviewReturnRequestBody();

    private ClientReviewReturnRequestBuilder() {
        body.setReasonCode(REASON_CODE);
        body.setSection(SECTION);
        body.setPublicNote(PUBLIC_NOTE);
    }

    /** 合法的退回请求。 */
    public static ClientReviewReturnRequestBuilder template() {
        return new ClientReviewReturnRequestBuilder();
    }

    public ClientReviewReturnRequestBuilder reasonCode(String value) {
        body.setReasonCode(value);
        return this;
    }

    public ClientReviewReturnRequestBuilder section(String value) {
        body.setSection(value);
        return this;
    }

    public ClientReviewReturnRequestBuilder publicNote(String value) {
        body.setPublicNote(value);
        return this;
    }

    public ClientReviewReturnRequestBody build() {
        return body;
    }
}
