package com.paytech.commercial.test.mock.external.compliance;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.paytech.commercial.test.mocker.compliance.QualificationEvaluationMocker;
import com.paytech.compliance.api.QualificationEvaluationApi;
import com.paytech.compliance.api.request.QualificationEvaluationRequestBody;
import com.paytech.compliance.api.response.QualificationEvaluationResponseBody;
import com.paytech.foundation.testsupport.api.MockHttpFault;

/** compliance 资格判定接口的测试 HTTP 实现；commercial 必须以 commercial-service 调用。 */
@RestController
@TestComponent
public class MockQualificationEvaluationApi implements QualificationEvaluationApi {
    private final QualificationEvaluationMocker mocker;

    /** 与用例共享配置与记录。 */
    public MockQualificationEvaluationApi(QualificationEvaluationMocker mocker) {
        this.mocker = mocker;
    }

    /** 返回本例配置的结论或故障；存在未配置主体时记为意外调用。 */
    @Override
    public QualificationEvaluationResponseBody evaluate(String callerService, QualificationEvaluationRequestBody request) {
        if (!"commercial-service".equals(callerService)) {
            throw new IllegalStateException("意外的调用方: " + callerService);
        }
        var attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        attributes.getResponse().setHeader("X-Request-Id", QualificationEvaluationMocker.REQUEST_ID);
        Object configured = mocker.evaluate(request);
        if (configured instanceof QualificationEvaluationMocker.Fault fault) {
            throw fault.disconnect() ? MockHttpFault.disconnect() : MockHttpFault.json(fault.status(), fault.problem());
        }
        if (configured == null) {
            throw new IllegalStateException("未配置的 compliance 资格主体");
        }
        return (QualificationEvaluationResponseBody) configured;
    }
}
