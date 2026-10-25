package com.paytech.compliance.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.compliance.api.QualificationEvaluationApi;
import com.paytech.compliance.api.request.QualificationEvaluationRequestBody;
import com.paytech.compliance.api.response.QualificationEvaluationResponseBody;
import com.paytech.compliance.core.enums.QualificationErrorCode;
import com.paytech.compliance.core.enums.QualificationRoleType;
import com.paytech.compliance.domain.QualificationSubject;
import com.paytech.compliance.service.processor.EvaluateQualificationsProcessor;
import com.paytech.compliance.service.processor.request.EvaluateQualificationsRequest;
import com.paytech.foundation.common.error.FailException;

import lombok.AllArgsConstructor;

/**
 * commercial Checkout 解析使用的资格判定入站；只做调用范围检查与协议转换，委托只读 Processor
 * <li>私网调用范围仅 commercial-service；Caller Header 只防止调用范围误用，实际身份由部署网络保证。</li>
 * <li>不受 onboarding.review.enabled 控制。</li>
 */
@RestController
@AllArgsConstructor
public class QualificationEvaluationController implements QualificationEvaluationApi {
    /** 唯一允许的调用方。 */
    static final String COMMERCIAL = "commercial-service";

    private final EvaluateQualificationsProcessor processor;

    /** 校验调用范围后批量判定。 */
    @Override
    public QualificationEvaluationResponseBody evaluate(String callerService, QualificationEvaluationRequestBody request) {
        if (!COMMERCIAL.equals(callerService)) {
            throw new FailException(QualificationErrorCode.QUALIFICATION_NOT_FOUND);
        }
        var subjects = request.getSubjects().stream()
            .map(subject -> new QualificationSubject(QualificationRoleType.ofCode(subject.getRoleType()), subject.getRoleId()))
            .toList();
        return QualificationWireConverter.evaluation(processor.process(new EvaluateQualificationsRequest(subjects)));
    }
}
