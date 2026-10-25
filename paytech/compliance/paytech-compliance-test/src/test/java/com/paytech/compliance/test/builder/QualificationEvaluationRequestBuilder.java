package com.paytech.compliance.test.builder;

import java.util.ArrayList;

import com.paytech.compliance.api.request.QualificationEvaluationRequestBody;
import com.paytech.compliance.api.request.QualificationSubjectBody;

/**
 * 资格判定请求 Builder；按添加顺序保留主体，build() 不去重也不修复非法值
 *
 * @author Kenny
 */
public final class QualificationEvaluationRequestBuilder {
    private final QualificationEvaluationRequestBody body = new QualificationEvaluationRequestBody();

    private QualificationEvaluationRequestBuilder() {
        body.setSubjects(new ArrayList<>());
    }

    /** 没有主体的请求。 */
    public static QualificationEvaluationRequestBuilder template() {
        return new QualificationEvaluationRequestBuilder();
    }

    /** 追加一个主体。 */
    public QualificationEvaluationRequestBuilder subject(String roleType, String roleId) {
        var subject = new QualificationSubjectBody();
        subject.setRoleType(roleType);
        subject.setRoleId(roleId);
        body.getSubjects().add(subject);
        return this;
    }

    public QualificationEvaluationRequestBody build() {
        return body;
    }
}
