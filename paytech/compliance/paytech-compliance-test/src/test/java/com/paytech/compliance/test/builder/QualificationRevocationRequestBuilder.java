package com.paytech.compliance.test.builder;

import com.paytech.compliance.api.request.QualificationRevocationRequestBody;

/**
 * 撤销请求 Builder；template(version) 给出合法原因与依据，build() 不修复刻意设置的非法值
 *
 * @author Kenny
 */
public final class QualificationRevocationRequestBuilder {
    public static final String REASON_CODE = "MANUAL_REVOKED";

    public static final String BASIS_NOTE = "Synthetic manual revocation basis";

    private final QualificationRevocationRequestBody body = new QualificationRevocationRequestBody();

    private QualificationRevocationRequestBuilder(long expectedVersion) {
        body.setExpectedVersion(Long.toString(expectedVersion));
        body.setReasonCode(REASON_CODE);
        body.setBasisNote(BASIS_NOTE);
    }

    /** 按页面读取版本撤销的合法请求。 */
    public static QualificationRevocationRequestBuilder template(long expectedVersion) {
        return new QualificationRevocationRequestBuilder(expectedVersion);
    }

    public QualificationRevocationRequestBuilder expectedVersion(String value) {
        body.setExpectedVersion(value);
        return this;
    }

    public QualificationRevocationRequestBuilder reasonCode(String value) {
        body.setReasonCode(value);
        return this;
    }

    public QualificationRevocationRequestBuilder basisNote(String value) {
        body.setBasisNote(value);
        return this;
    }

    public QualificationRevocationRequestBody build() {
        return body;
    }
}
