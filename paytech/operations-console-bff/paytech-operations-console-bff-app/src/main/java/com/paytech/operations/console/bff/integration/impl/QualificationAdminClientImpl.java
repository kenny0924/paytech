package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.ClientReviewRiskClientImpl.require;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.compliance.api.QualificationAdminApi;
import com.paytech.compliance.api.enums.QualificationRoleType;
import com.paytech.compliance.api.request.QualificationManualDecisionRequestBody;
import com.paytech.compliance.api.request.QualificationRevocationRequestBody;
import com.paytech.compliance.api.response.QualificationHistoryResponseBody;
import com.paytech.compliance.api.response.QualificationResponseBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.QualificationAdminClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import lombok.AllArgsConstructor;

/** 调用 compliance 资格管理合同；按主体或资格读取的结果必须对应请求，否则视为上游响应无效。 */
@Component
@AllArgsConstructor
public class QualificationAdminClientImpl implements QualificationAdminClient {
    /** 一个主体的资格每种类型最多一条，第一页取 50 条足以容纳全部类型。 */
    static final int SUBJECT_PAGE_SIZE = 50;

    private final QualificationAdminApi api;

    @Override
    public PagingResponseBody<QualificationResponseBody> list(OperatorContext o, String roleType, String roleId,
        String qualificationType, String status, Integer page, Integer size) {
        var response = api.listQualifications(o.accountId(), o.sessionId(), roleType, roleId, qualificationType, status, page, size);
        require(response != null && response.items() != null);
        return response;
    }

    @Override
    public List<QualificationResponseBody> subjectQualifications(OperatorContext o, QualificationRoleType roleType, String roleId) {
        var response = list(o, roleType.getCode(), roleId, null, null, 1, SUBJECT_PAGE_SIZE);
        require(response.items().stream()
            .allMatch(item -> roleType.getCode().equals(item.getRoleType()) && roleId.equals(item.getRoleId())));
        return response.items();
    }

    @Override
    public QualificationHistoryResponseBody history(OperatorContext o, String qualificationId) {
        var response = api.getQualificationHistory(o.accountId(), o.sessionId(), qualificationId);
        require(response != null && qualificationId.equals(response.getQualificationId()) && response.getItems() != null);
        return response;
    }

    @Override
    public QualificationResponseBody recordManualDecision(OperatorContext o, QualificationManualDecisionRequestBody request) {
        var response = api.recordManualDecision(o.accountId(), o.sessionId(), request);
        require(response != null && request.getRoleType().equals(response.getRoleType())
            && request.getRoleId().equals(response.getRoleId())
            && request.getQualificationType().equals(response.getQualificationType()));
        return response;
    }

    @Override
    public QualificationResponseBody revoke(OperatorContext o, String qualificationId, QualificationRevocationRequestBody request) {
        var response = api.revokeQualification(o.accountId(), o.sessionId(), qualificationId, request);
        require(response != null && qualificationId.equals(response.getQualificationId()));
        return response;
    }
}
