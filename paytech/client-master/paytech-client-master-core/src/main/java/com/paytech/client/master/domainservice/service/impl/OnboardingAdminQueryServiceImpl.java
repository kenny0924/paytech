package com.paytech.client.master.domainservice.service.impl;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.paytech.client.master.api.enums.CheckoutCandidateErrorCode;
import com.paytech.client.master.api.enums.EntityAdminErrorCode;
import com.paytech.client.master.core.dal.dataobject.CatalogItem;
import com.paytech.client.master.core.dal.dataobject.ClientApplicationSubmission;
import com.paytech.client.master.core.dal.dataobject.OnboardingSchema;
import com.paytech.client.master.core.dal.dataobject.OrganizationType;
import com.paytech.client.master.core.dal.dataobject.PartnerOnboardingCode;
import com.paytech.client.master.domain.onboarding.pack.OnboardingPack;
import com.paytech.client.master.domain.onboardingadmin.ClientAdminView;
import com.paytech.client.master.domain.onboardingadmin.JurisdictionAdminDetail;
import com.paytech.client.master.domain.onboardingadmin.SchemaAdminDetail;
import com.paytech.client.master.domainservice.repository.CatalogItemRepository;
import com.paytech.client.master.domainservice.repository.ClientApplicationRepository;
import com.paytech.client.master.domainservice.repository.ClientRepository;
import com.paytech.client.master.domainservice.repository.IdentifierTypeRepository;
import com.paytech.client.master.domainservice.repository.JurisdictionRepository;
import com.paytech.client.master.domainservice.repository.LocalPartnerRepository;
import com.paytech.client.master.domainservice.repository.OnboardingSchemaFieldRepository;
import com.paytech.client.master.domainservice.repository.OnboardingSchemaRepository;
import com.paytech.client.master.domainservice.repository.OrganizationTypeRepository;
import com.paytech.client.master.domainservice.repository.PartnerOnboardingCodeRepository;
import com.paytech.client.master.domainservice.repository.RelationTypeRepository;
import com.paytech.client.master.domainservice.repository.RuleSettingRepository;
import com.paytech.client.master.domainservice.service.OnboardingAdminQueryService;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** 运营只读查询：组合辖区包目录、Schema 字段、Client 与入驻码；只读本库。 */
@AllArgsConstructor
public class OnboardingAdminQueryServiceImpl implements OnboardingAdminQueryService {
    private final ClientRepository clients;
    private final CatalogItemRepository catalogItems;
    private final RuleSettingRepository ruleSettings;
    private final JurisdictionRepository jurisdictions;
    private final LocalPartnerRepository localPartners;
    private final RelationTypeRepository relationTypes;
    private final OnboardingSchemaRepository schemas;
    private final IdentifierTypeRepository identifierTypes;
    private final ClientApplicationRepository applications;
    private final OrganizationTypeRepository organizationTypes;
    private final OnboardingSchemaFieldRepository schemaFields;
    private final PartnerOnboardingCodeRepository onboardingCodes;

    /** 每个辖区的规则取其辖区、组织类型、标识类型作用域与 * 默认。 */
    @Override
    public List<JurisdictionAdminDetail> jurisdictions() {
        var allRules = ruleSettings.findAll();
        return jurisdictions.findAll().stream().map(jurisdiction -> {
            String code = jurisdiction.getJurisdictionCode();
            List<OrganizationType> types = organizationTypes.findByJurisdiction(code);
            var identifiers = identifierTypes.findForJurisdiction(code);
            Set<String> scopes = new LinkedHashSet<>(List.of(OnboardingPack.ANY, code));
            types.forEach(type -> scopes.add(code + ":" + type.getTypeCode()));
            identifiers.forEach(type -> scopes.add(type.getIdentifierTypeCode()));
            return new JurisdictionAdminDetail(jurisdiction, allRules.stream().filter(rule -> scopes.contains(rule.getScopeCode())).toList(),
                types, identifiers, relationTypes.findForJurisdiction(code));
        }).toList();
    }

    /** 辖区全部版本，字段一次读出。 */
    @Override
    public List<SchemaAdminDetail> schemas(String jurisdictionCode) {
        List<OnboardingSchema> versions = schemas.findByJurisdiction(jurisdictionCode);
        var fields = schemaFields.findBySchemas(versions.stream().map(OnboardingSchema::getSchemaId).toList());
        return versions.stream().map(schema -> new SchemaAdminDetail(schema,
            fields.stream().filter(field -> field.getSchemaId().equals(schema.getSchemaId())).toList())).toList();
    }

    /** 某目录全部项。 */
    @Override
    public List<CatalogItem> catalogItems(String catalogCode) {
        return catalogItems.findByCatalog(catalogCode);
    }

    /** 数据库分页与同条件计数。 */
    @Override
    public PagingResponseBody<ClientAdminView> clients(String status, String jurisdictionCode, Long responsiblePartnerId,
        PagingRequestBody paging) {
        return new PagingResponseBody<>(clients.pageForAdmin(status, jurisdictionCode, responsiblePartnerId, paging.size(),
            paging.offset()), paging.page(), paging.size(), clients.countForAdmin(status, jurisdictionCode, responsiblePartnerId));
    }

    /** 详情行。 */
    @Override
    public ClientAdminView client(long clientId) {
        ClientAdminView view = clients.findAdminView(clientId);
        if (view == null) {
            throw new FailException(CheckoutCandidateErrorCode.CLIENT_NOT_FOUND);
        }
        return view;
    }

    /** 申请历史。 */
    @Override
    public List<ClientApplicationSubmission> applications(long clientId) {
        return applications.findByClient(clientId);
    }

    /** Partner 须存在。 */
    @Override
    public List<PartnerOnboardingCode> onboardingCodes(long localPartnerId) {
        if (localPartners.findById(localPartnerId) == null) {
            throw new FailException(EntityAdminErrorCode.LOCAL_PARTNER_NOT_FOUND);
        }
        return onboardingCodes.findByPartner(localPartnerId);
    }
}
