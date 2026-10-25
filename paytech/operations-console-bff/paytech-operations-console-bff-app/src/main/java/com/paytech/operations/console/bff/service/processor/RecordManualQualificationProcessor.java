package com.paytech.operations.console.bff.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.compliance.api.response.QualificationResponseBody;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.domain.enums.QualificationSubjectTerminalStatus;
import com.paytech.operations.console.bff.integration.EntityAdminClient;
import com.paytech.operations.console.bff.integration.QualificationAdminClient;
import com.paytech.operations.console.bff.service.processor.request.RecordManualQualificationRequest;

import lombok.AllArgsConstructor;

/**
 * B10 手工登记资格：compliance 不出站核对主体，由 BFF 先向 client-master 确认主体存在且未终结，再单次写入 compliance
 * <li>主体不存在：client-master 的 404（OPERATING_ENTITY_NOT_FOUND、LOCAL_PARTNER_NOT_FOUND、CLIENT_NOT_FOUND）原样返回，不调用 compliance。</li>
 * <li>OE 已 CLOSED、Partner 或 Client 已 TERMINATED：422 QUALIFICATION_SUBJECT_TERMINATED，不调用 compliance。</li>
 * <li>核对与写入之间主体状态仍可能变化，这是 compliance 不校验主体时可接受的窗口；compliance 的 409 / 422 原样返回，不重试。</li>
 */
@Component
@AllArgsConstructor
public class RecordManualQualificationProcessor extends AbstractProcessor<RecordManualQualificationRequest, QualificationResponseBody> {
    private final EntityAdminClient entityAdmin;
    private final QualificationAdminClient qualifications;

    @Override
    protected QualificationResponseBody processRequest(RecordManualQualificationRequest request) {
        var decision = request.getDecision();
        var roleType = QualificationListProcessor.roleTypeOf(decision.getRoleType());
        var subject = entityAdmin.subject(request.getOperator(), roleType, decision.getRoleId());
        if (QualificationSubjectTerminalStatus.isTerminal(roleType, subject.getStatus())) {
            throw new FailException(OperationsBffErrorCode.QUALIFICATION_SUBJECT_TERMINATED,
                "主体已关闭或终止，不能登记资格: " + roleType.getCode() + " " + decision.getRoleId() + ", 状态: " + subject.getStatus());
        }
        return qualifications.recordManualDecision(request.getOperator(), decision);
    }
}
