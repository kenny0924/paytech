package com.paytech.client.master.service.processor;

import com.paytech.client.master.api.enums.OnboardingErrorCode;
import com.paytech.client.master.api.onboarding.response.FormSchemaResponseBody;
import com.paytech.client.master.core.dal.dataobject.ClientDraftIdentity;
import com.paytech.client.master.core.dal.dataobject.Jurisdiction;
import com.paytech.client.master.core.dal.dataobject.OnboardingSchema;
import com.paytech.client.master.domain.enums.AcceptancePolicy;
import com.paytech.client.master.domain.enums.ClientType;
import com.paytech.client.master.domain.enums.RuleCode;
import com.paytech.client.master.domain.onboarding.ClientDraftPolicy;
import com.paytech.client.master.domain.onboarding.pack.OnboardingPack;
import com.paytech.client.master.domainservice.repository.ClientDraftRepository;
import com.paytech.client.master.domainservice.service.OnboardingPackService;
import com.paytech.client.master.ext.rest.FormSchemaConverter;
import com.paytech.client.master.integration.ClientDraftAccountClient;
import com.paytech.client.master.integration.ComplianceProgramClient;
import com.paytech.client.master.service.processor.request.GetFormSchemaRequest;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * CM-2 表单渲染规格：申请已创建时返回其冻结的 Schema 与责任方材料；否则按辖区、主体类型与组织类型解析当前 ACTIVE Schema（平台程序材料）。
 * <li>只读本库目录，事务外调用 compliance 程序解析；程序不可用时整体 503，不返回缺少材料的半成品。</li>
 */
@AllArgsConstructor
public class GetOnboardingFormSchemaProcessor extends AbstractProcessor<GetFormSchemaRequest, FormSchemaResponseBody> {
    private final OnboardingPackService packs;
    private final ClientDraftRepository drafts;
    private final ClientDraftAccountClient accounts;
    private final ComplianceProgramClient programs;

    /** 会话 → Schema 定位 → 程序材料 → 组装。 */
    @Override
    protected FormSchemaResponseBody processRequest(GetFormSchemaRequest request) {
        accounts.authorize(request.getAccountId(), request.getSessionId());
        if (request.getApplicationId() != null) {
            ClientDraftIdentity application = ClientDraftPolicy.requireValid(
                drafts.identity(request.getApplicationId(), request.getAccountId(), false));
            OnboardingPack pack = packs.load(application.schemaId());
            return FormSchemaConverter.convert(pack, application.clientType(), application.organizationTypeCode(),
                programs.materials(OnboardingScopes.scope(pack, application.clientType(), application.responsiblePartnerId())));
        }
        ClientType partyType = ClientType.INDIVIDUAL.getCode().equals(request.getPartyType()) ? ClientType.INDIVIDUAL
            : ClientType.ORGANIZATION.getCode().equals(request.getPartyType()) ? ClientType.ORGANIZATION : null;
        if (partyType == null || request.getJurisdictionCode() == null) {
            throw new InvalidParameterException("未提供 applicationId 时 jurisdiction 与 partyType 必填");
        }
        OnboardingPack pack = packs.load(acceptedSchema(request.getJurisdictionCode(), partyType, request.getOrganizationTypeCode())
            .getSchemaId());
        return FormSchemaConverter.convert(pack, partyType, request.getOrganizationTypeCode(),
            programs.materials(OnboardingScopes.scope(pack, partyType, null)));
    }

    /** 辖区 ACTIVE、准入策略不是 CLOSED 且有可用 Schema，否则 JURISDICTION_NOT_ACCEPTED。 */
    OnboardingSchema acceptedSchema(String jurisdictionCode, ClientType partyType, String organizationTypeCode) {
        Jurisdiction jurisdiction = packs.jurisdiction(jurisdictionCode);
        if (jurisdiction == null || !OnboardingPack.active(jurisdiction.getStatus())) {
            throw new FailException(OnboardingErrorCode.JURISDICTION_NOT_ACCEPTED);
        }
        AcceptancePolicy policy = AcceptancePolicy.fromCode(packs.rules(jurisdictionCode)
            .value(RuleCode.ACCEPTANCE_POLICY, jurisdictionCode));
        OnboardingSchema schema = packs.resolveSchema(jurisdictionCode, partyType, organizationTypeCode);
        if (policy == null || policy == AcceptancePolicy.CLOSED || schema == null) {
            throw new FailException(OnboardingErrorCode.JURISDICTION_NOT_ACCEPTED);
        }
        return schema;
    }
}
