package com.paytech.client.master.integration.impl;

import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.ONBOARDING_DEPENDENCY_UNAVAILABLE;

import java.util.ArrayList;
import java.util.List;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.client.master.domain.enums.ClientType;
import com.paytech.client.master.domain.onboarding.ProgramScope;
import com.paytech.client.master.domain.onboarding.requirement.MaterialRequirement;
import com.paytech.client.master.integration.ComplianceProgramClient;
import com.paytech.compliance.api.ProgramResolutionApi;
import com.paytech.compliance.api.response.ProgramMaterialBody;
import com.paytech.compliance.api.response.ProgramResolutionResponseBody;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 调用 compliance programs/resolve 并把材料槽转换为本服务的需求计算输入；不缓存程序。 */
@AllArgsConstructor
public class ComplianceProgramClientImpl implements ComplianceProgramClient {
    private static final String CALLER = "client-master-service";
    private static final String ROLE_CLIENT = "CLIENT";
    private static final String ROLE_RELATED_PERSON = "RELATED_PERSON";
    private static final String SUBJECT_SELF = "PRIMARY";
    private static final String ANY_RELATED = "ANY_RELATED";

    private final ProgramResolutionApi programs;

    /** 主主体程序，组织申请再加关联人员程序。 */
    @Override
    public List<MaterialRequirement> materials(ProgramScope scope) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("compliance 调用不能位于数据库事务内");
        }
        var result = new ArrayList<MaterialRequirement>();
        resolve(scope, scope.partyType(), ROLE_CLIENT).forEach(material -> result.add(material(material, material.getSubjectRole())));
        if (scope.includeRelated()) {
            resolve(scope, ClientType.INDIVIDUAL, ROLE_RELATED_PERSON).forEach(material -> result.add(material(material,
                SUBJECT_SELF.equals(material.getSubjectRole()) ? ANY_RELATED : material.getSubjectRole())));
        }
        return List.copyOf(result);
    }

    /** 一次程序解析；响应不完整失败关闭。 */
    List<ProgramMaterialBody> resolve(ProgramScope scope, ClientType partyType, String roleType) {
        ProgramResolutionResponseBody response;
        try {
            response = programs.resolve(CALLER, scope.jurisdictionCode(), partyType.getCode(), roleType, scope.tier(),
                scope.responsiblePartnerId() == null ? null : scope.responsiblePartnerId().toString());
        } catch (RuntimeException unavailable) {
            throw new ErrorException(ONBOARDING_DEPENDENCY_UNAVAILABLE);
        }
        if (response == null || response.getProgramVersionId() == null || response.getMaterials() == null
            || response.getMaterials().stream().anyMatch(item -> item == null || !code(item.getRequirementCode())
                || item.getSubjectRole() == null)) {
            throw new ErrorException(ONBOARDING_DEPENDENCY_UNAVAILABLE);
        }
        return response.getMaterials();
    }

    /** 转换为需求计算输入。 */
    static MaterialRequirement material(ProgramMaterialBody body, String subjectRole) {
        return new MaterialRequirement(body.getRequirementCode(), subjectRole,
            body.getDocumentTypes() == null ? List.of() : List.copyOf(body.getDocumentTypes()), Boolean.TRUE.equals(body.getRequired()),
            body.getMaxAgeDays(), Boolean.TRUE.equals(body.getWaivable()));
    }

    /** 稳定材料代码。 */
    static boolean code(String value) {
        return value != null && value.matches("[A-Z][A-Z0-9_]{0,63}");
    }
}
