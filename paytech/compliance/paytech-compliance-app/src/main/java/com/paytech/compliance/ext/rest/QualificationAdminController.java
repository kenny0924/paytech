package com.paytech.compliance.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.compliance.api.QualificationAdminApi;
import com.paytech.compliance.api.request.QualificationManualDecisionRequestBody;
import com.paytech.compliance.api.request.QualificationRevocationRequestBody;
import com.paytech.compliance.api.response.QualificationHistoryResponseBody;
import com.paytech.compliance.api.response.QualificationResponseBody;
import com.paytech.compliance.core.enums.QualificationRoleType;
import com.paytech.compliance.core.enums.QualificationStatus;
import com.paytech.compliance.core.enums.QualificationType;
import com.paytech.compliance.service.processor.GetQualificationHistoryProcessor;
import com.paytech.compliance.service.processor.ListQualificationsProcessor;
import com.paytech.compliance.service.processor.RecordManualQualificationProcessor;
import com.paytech.compliance.service.processor.RevokeQualificationProcessor;
import com.paytech.compliance.service.processor.request.ListQualificationsRequest;
import com.paytech.compliance.service.processor.request.QualificationHistoryRequest;
import com.paytech.compliance.service.processor.request.RecordManualQualificationRequest;
import com.paytech.compliance.service.processor.request.RevokeQualificationRequest;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.foundation.web.request.OperationRequestContextParser;

import lombok.AllArgsConstructor;

/**
 * Operations 资格登记页入站：解析 Gateway 可信操作者、Query 与路径参数后委托 Processor
 * <li>不受 onboarding.review.enabled 控制。</li>
 */
@RestController
@AllArgsConstructor
public class QualificationAdminController implements QualificationAdminApi {
    private final OperationRequestContextParser parser;
    private final ListQualificationsProcessor listProcessor;
    private final RevokeQualificationProcessor revokeProcessor;
    private final GetQualificationHistoryProcessor historyProcessor;
    private final RecordManualQualificationProcessor manualProcessor;

    /** 解析筛选条件与公共分页后查询。 */
    @Override
    public PagingResponseBody<QualificationResponseBody> listQualifications(String actorAccountId, String sessionId,
        String roleType, String roleId, String qualificationType, String status, Integer page, Integer size) {
        parser.parse(actorAccountId, sessionId);
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        var request = new ListQualificationsRequest(paging.page(), paging.size(), paging.offset(),
            code(QualificationRoleType.ofCode(parser.parseOptionalUpperCode("roleType", roleType)), roleType, "roleType"),
            roleId, code(QualificationType.ofCode(parser.parseOptionalUpperCode("qualificationType", qualificationType)),
                qualificationType, "qualificationType"),
            code(QualificationStatus.ofCode(parser.parseOptionalUpperCode("status", status)), status, "status"));
        var result = listProcessor.process(request);
        return new PagingResponseBody<>(result.getItems().stream().map(QualificationWireConverter::qualification).toList(),
            result.getPage(), result.getSize(), result.getTotal());
    }

    /** 一个资格的状态变更历史。 */
    @Override
    public QualificationHistoryResponseBody getQualificationHistory(String actorAccountId, String sessionId,
        String qualificationId) {
        parser.parse(actorAccountId, sessionId);
        return QualificationWireConverter.history(historyProcessor.process(
            new QualificationHistoryRequest(parser.parsePositiveId("qualificationId", qualificationId))));
    }

    /** 手工登记；角色与资格类型的 Code 已由 Body 校验。 */
    @Override
    public QualificationResponseBody recordManualDecision(String actorAccountId, String sessionId,
        QualificationManualDecisionRequestBody request) {
        var context = parser.parse(actorAccountId, sessionId);
        return QualificationWireConverter.qualification(manualProcessor.process(new RecordManualQualificationRequest(
            context.getActorAccountId(), QualificationRoleType.ofCode(request.getRoleType()), request.getRoleId(),
            QualificationType.ofCode(request.getQualificationType()), QualificationWireConverter.instant(request.getValidFrom(), "validFrom"),
            QualificationWireConverter.instant(request.getValidTo(), "validTo"), request.getReasonCode(), request.getBasisNote(),
            QualificationWireConverter.version(request.getExpectedVersion()))));
    }

    /** 撤销。 */
    @Override
    public QualificationResponseBody revokeQualification(String actorAccountId, String sessionId, String qualificationId,
        QualificationRevocationRequestBody request) {
        var context = parser.parse(actorAccountId, sessionId);
        return QualificationWireConverter.qualification(revokeProcessor.process(new RevokeQualificationRequest(
            context.getActorAccountId(), parser.parsePositiveId("qualificationId", qualificationId),
            QualificationWireConverter.version(request.getExpectedVersion()), request.getReasonCode(), request.getBasisNote())));
    }

    /** 可选筛选 Code：未提供返回 null，提供了但不在目录中按参数错误拒绝。 */
    static <T> T code(T parsed, String raw, String field) {
        if (raw != null && parsed == null) {
            throw new InvalidParameterException(field + " 取值无效");
        }
        return parsed;
    }
}
