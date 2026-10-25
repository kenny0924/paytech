package com.paytech.operations.console.bff.ext.rest;

import java.util.Arrays;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.compliance.api.enums.QualificationRoleType;
import com.paytech.compliance.api.request.QualificationManualDecisionRequestBody;
import com.paytech.compliance.api.request.QualificationRevocationRequestBody;
import com.paytech.compliance.api.response.QualificationHistoryResponseBody;
import com.paytech.compliance.api.response.QualificationResponseBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.EntityAdminClient;
import com.paytech.operations.console.bff.integration.QualificationAdminClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.qualificationadmin.response.QualificationListItemResponseBody;
import com.paytech.operations.console.bff.service.model.qualificationadmin.response.QualificationSubjectResponseBody;
import com.paytech.operations.console.bff.service.processor.QualificationListProcessor;
import com.paytech.operations.console.bff.service.processor.RecordManualQualificationProcessor;
import com.paytech.operations.console.bff.service.processor.request.QualificationListRequest;
import com.paytech.operations.console.bff.service.processor.request.RecordManualQualificationRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * B7—B11 角色资格登记；路由权限由 Gateway 路由码 + PLT-I02 统一授权（块 137）
 * <li>B7 按角色读取 client-master 主体；B8 列表补主体名称、B10 登记前核对主体，由 Processor 编排；B9、B11 转发 compliance。</li>
 * <li>请求与资格响应使用 compliance 管理合同的模型；写入按资格 version 防覆盖，不转发 Idempotency-Key。</li>
 * <li>依据说明与主体名称只记元数据日志（/qualification-admin）。</li>
 */
@RestController
@AllArgsConstructor
@RequestMapping("/qualification-admin/v1")
public class QualificationAdminController {
    private static final String ID = "^[1-9][0-9]{0,18}$";
    private static final String CODE = "^[A-Z][A-Z0-9_]{0,63}$";
    private static final String ROLE_TYPE = "^(CLIENT|OPERATING_ENTITY|LOCAL_PARTNER)$";
    private static final String SUBJECT_REF = "^([A-Z]{2}[0-9]{3}|[1-9][0-9]{0,18})$";

    private final EntityAdminClient entityAdmin;
    private final QualificationAdminClient qualifications;
    private final QualificationListProcessor list;
    private final RecordManualQualificationProcessor manualDecision;

    /** B7 主体核对：把 entityNo / localPartnerId / clientId 解析为角色标识、名称与状态；不存在返回 client-master 的 404。 */
    @GetMapping("/subjects")
    public QualificationSubjectResponseBody subject(OperatorContext operator,
        @RequestParam @NotBlank @Pattern(regexp = ROLE_TYPE) String roleType,
        @RequestParam @NotBlank @Pattern(regexp = SUBJECT_REF) String subjectRef) {
        QualificationRoleType type = Arrays.stream(QualificationRoleType.values())
            .filter(value -> value.getCode().equals(roleType)).findFirst().orElseThrow();
        return entityAdmin.subject(operator, type, subjectRef);
    }

    /** B8 资格列表，按更新时间倒序，每行补主体名称；size 只允许 10、20、50、100、200（compliance 校验）。 */
    @GetMapping("/qualifications")
    public PagingResponseBody<QualificationListItemResponseBody> list(OperatorContext operator,
        @RequestParam(required = false) @Pattern(regexp = ROLE_TYPE) String roleType,
        @RequestParam(required = false) @Pattern(regexp = SUBJECT_REF) String roleId,
        @RequestParam(required = false) @Pattern(regexp = CODE) String qualificationType,
        @RequestParam(required = false) @Pattern(regexp = CODE) String status,
        @RequestParam(required = false) @Min(1) @Max(100000) Integer page,
        @RequestParam(required = false) @Min(1) @Max(200) Integer size) {
        return list.process(new QualificationListRequest(operator, roleType, roleId, qualificationType, status, page, size));
    }

    /** B9 资格状态变更与对应决定，按资格版本倒序。 */
    @GetMapping("/qualifications/{qualificationId}/history")
    public QualificationHistoryResponseBody history(OperatorContext operator,
        @PathVariable @Pattern(regexp = ID) String qualificationId) {
        return qualifications.history(operator, qualificationId);
    }

    /** B10 手工登记：先核对主体存在且未终结，再写入 compliance。 */
    @PostMapping("/qualifications/manual-decisions")
    public QualificationResponseBody recordManualDecision(OperatorContext operator,
        @Valid @RequestBody QualificationManualDecisionRequestBody request) {
        return manualDecision.process(new RecordManualQualificationRequest(operator, request));
    }

    /** B11 撤销一条当前已批准的资格。 */
    @PostMapping("/qualifications/{qualificationId}/revocation")
    public QualificationResponseBody revoke(OperatorContext operator, @PathVariable @Pattern(regexp = ID) String qualificationId,
        @Valid @RequestBody QualificationRevocationRequestBody request) {
        return qualifications.revoke(operator, qualificationId, request);
    }
}
