package com.paytech.client.master.domainservice.service.impl;

import com.paytech.client.contract.api.enums.ClientDraftErrorCode;
import com.paytech.foundation.common.error.FailException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.paytech.client.master.core.dal.dataobject.AttributeDefinition;
import com.paytech.client.master.core.dal.dataobject.IdentifierType;
import com.paytech.client.master.core.dal.dataobject.Jurisdiction;
import com.paytech.client.master.core.dal.dataobject.OnboardingSchema;
import com.paytech.client.master.core.dal.dataobject.OnboardingSchemaField;
import com.paytech.client.master.core.dal.dataobject.OrganizationType;
import com.paytech.client.master.domain.enums.ClientType;
import com.paytech.client.master.domain.enums.WidgetCode;
import com.paytech.client.master.domain.onboarding.pack.OnboardingPack;
import com.paytech.client.master.domain.onboarding.pack.ReferenceCatalogCodes;
import com.paytech.client.master.domain.onboarding.pack.RuleSettings;
import com.paytech.client.master.domainservice.repository.AddressFormatComponentRepository;
import com.paytech.client.master.domainservice.repository.AttributeDefinitionRepository;
import com.paytech.client.master.domainservice.repository.CatalogItemRepository;
import com.paytech.client.master.domainservice.repository.IdentifierTypeRepository;
import com.paytech.client.master.domainservice.repository.JurisdictionRepository;
import com.paytech.client.master.domainservice.repository.OnboardingSchemaFieldRepository;
import com.paytech.client.master.domainservice.repository.OnboardingSchemaRepository;
import com.paytech.client.master.domainservice.repository.OrganizationTypeRepository;
import com.paytech.client.master.domainservice.repository.RelationTypeRepository;
import com.paytech.client.master.domainservice.repository.RuleSettingRepository;
import com.paytech.client.master.domainservice.service.OnboardingPackService;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 辖区包装载：所有目录只取 ACTIVE 行；冻结 Schema 即使已 RETIRED 也照常装载（在途申请不受新版本影响）。 */
@AllArgsConstructor
public class OnboardingPackServiceImpl implements OnboardingPackService {
    private static final String ROLE_CLIENT = "CLIENT";

    private final CatalogItemRepository catalogItems;
    private final JurisdictionRepository jurisdictions;
    private final RuleSettingRepository ruleSettings;
    private final RelationTypeRepository relationTypes;
    private final IdentifierTypeRepository identifierTypes;
    private final OnboardingSchemaRepository schemas;
    private final OrganizationTypeRepository organizationTypes;
    private final OnboardingSchemaFieldRepository schemaFields;
    private final AttributeDefinitionRepository attributeDefinitions;
    private final AddressFormatComponentRepository addressComponents;

    /** 一次读出 Schema 与辖区包全部目录，过滤为 ACTIVE 后组装。 */
    @Override
    public OnboardingPack load(Long schemaId) {
        if (schemaId == null) {
            throw new FailException(ClientDraftErrorCode.APPLICATION_NOT_EDITABLE);
        }
        OnboardingSchema schema = schemas.findById(schemaId);
        Jurisdiction jurisdiction = schema == null ? null : jurisdictions.find(schema.getJurisdictionCode());
        if (jurisdiction == null) {
            throw new ErrorException("申请冻结的采集 Schema 或辖区包不存在: " + schemaId);
        }
        String code = jurisdiction.getJurisdictionCode();
        List<OnboardingSchemaField> fields = schemaFields.findBySchemas(List.of(schemaId));
        List<OrganizationType> organizations = organizationTypes.findByJurisdiction(code).stream()
            .filter(row -> OnboardingPack.active(row.getStatus())).toList();
        List<IdentifierType> identifiers = identifierTypes.findForJurisdiction(code).stream()
            .filter(row -> OnboardingPack.active(row.getStatus())).toList();
        List<AttributeDefinition> attributes = attributeDefinitions.findForJurisdiction(code).stream()
            .filter(row -> OnboardingPack.active(row.getStatus())).toList();
        List<String> scopes = new ArrayList<>(declarationTypes(fields));
        attributes.forEach(attribute -> scopes.add(attribute.getAttributeCode()));
        return new OnboardingPack(jurisdiction, rules(code, scopes), organizations, identifiers,
            relationTypes.findForJurisdiction(code).stream().filter(row -> OnboardingPack.active(row.getStatus())).toList(),
            addressComponents.findByFormats(List.of(jurisdiction.getAddressFormatCode(), OnboardingPack.GENERIC_ADDRESS_FORMAT)),
            catalogItems.findActive(catalogCodes(fields, attributes, jurisdiction), code), attributes, schema, fields);
    }

    /** 专用组织类型版本优先，其次 * 默认版本。 */
    @Override
    public OnboardingSchema resolveSchema(String jurisdictionCode, ClientType partyType, String organizationTypeCode) {
        if (organizationTypeCode != null) {
            OnboardingSchema specific = schemas.findActive(jurisdictionCode, partyType.getCode(), ROLE_CLIENT, organizationTypeCode);
            if (specific != null) {
                return specific;
            }
        }
        return schemas.findActive(jurisdictionCode, partyType.getCode(), ROLE_CLIENT, OnboardingPack.ANY);
    }

    /** 读取辖区包。 */
    @Override
    public Jurisdiction jurisdiction(String jurisdictionCode) {
        return jurisdictions.find(jurisdictionCode);
    }

    /** 全部辖区包。 */
    @Override
    public List<Jurisdiction> jurisdictions() {
        return jurisdictions.findAll();
    }

    /** 辖区、其组织类型与可用标识类型的规则，以及三层 * 默认。 */
    @Override
    public RuleSettings rules(String jurisdictionCode) {
        return rules(jurisdictionCode, List.of());
    }

    /** 在辖区规则之外加上 Schema 声明类型的 DECLARATION 规则（如 DOCUMENT_VERSION）与扩展属性的 ATTRIBUTE 规则（如 DATE_NOT_PAST）。 */
    RuleSettings rules(String jurisdictionCode, List<String> extraScopes) {
        Set<String> scopes = new LinkedHashSet<>(extraScopes);
        scopes.add(OnboardingPack.ANY);
        scopes.add(jurisdictionCode);
        organizationTypes.findByJurisdiction(jurisdictionCode).forEach(row -> scopes.add(jurisdictionCode + ":" + row.getTypeCode()));
        identifierTypes.findForJurisdiction(jurisdictionCode).forEach(row -> scopes.add(row.getIdentifierTypeCode()));
        return new RuleSettings(ruleSettings.findActive(scopes));
    }

    /** Schema 中 declaration.<类型> 字段的声明类型。 */
    static List<String> declarationTypes(List<OnboardingSchemaField> fields) {
        return fields.stream().filter(field -> field.getWidgetCode() == WidgetCode.DECLARATION)
            .map(field -> field.getFieldKey().substring("declaration.".length())).toList();
    }

    /** Schema 字段、属性定义与地址组件引用的本库目录；REF_ 目录来自 Platform 参考数据，不从 t_catalog_item 加载。 */
    List<String> catalogCodes(List<OnboardingSchemaField> fields, List<AttributeDefinition> attributes, Jurisdiction jurisdiction) {
        Set<String> codes = new LinkedHashSet<>();
        fields.stream().map(OnboardingSchemaField::getCatalogCode).filter(java.util.Objects::nonNull).forEach(codes::add);
        attributes.stream().map(AttributeDefinition::getCatalogCode).filter(java.util.Objects::nonNull).forEach(codes::add);
        addressComponents.findByFormats(List.of(jurisdiction.getAddressFormatCode())).stream()
            .map(component -> component.getCatalogCode()).filter(java.util.Objects::nonNull).forEach(codes::add);
        codes.remove(OnboardingPack.ORGANIZATION_TYPE_CATALOG);
        codes.removeIf(ReferenceCatalogCodes::isReference);
        return new ArrayList<>(codes);
    }
}
