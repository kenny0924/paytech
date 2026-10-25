package com.paytech.operations.console.bff.integration;

import java.util.List;

import com.paytech.compliance.api.enums.QualificationRoleType;
import com.paytech.compliance.api.request.QualificationManualDecisionRequestBody;
import com.paytech.compliance.api.request.QualificationRevocationRequestBody;
import com.paytech.compliance.api.response.QualificationHistoryResponseBody;
import com.paytech.compliance.api.response.QualificationResponseBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

/** compliance 角色资格管理：查询、历史、手工登记与撤销；单次调用，不自动重试。 */
public interface QualificationAdminClient {
    /** B8 资格分页。 */
    PagingResponseBody<QualificationResponseBody> list(OperatorContext operator, String roleType, String roleId,
        String qualificationType, String status, Integer page, Integer size);

    /**
     * B2 / B5 一个主体的全部资格
     *
     * @param operator 当前操作人
     * @param roleType 资格角色类型
     * @param roleId 角色标识
     * @return 该主体各资格类型的当前资格（每种类型最多一条），按更新时间倒序
     */
    List<QualificationResponseBody> subjectQualifications(OperatorContext operator, QualificationRoleType roleType, String roleId);

    /** B9 资格状态变更历史。 */
    QualificationHistoryResponseBody history(OperatorContext operator, String qualificationId);

    /** B10 手工登记。 */
    QualificationResponseBody recordManualDecision(OperatorContext operator, QualificationManualDecisionRequestBody request);

    /** B11 撤销。 */
    QualificationResponseBody revoke(OperatorContext operator, String qualificationId, QualificationRevocationRequestBody request);
}
