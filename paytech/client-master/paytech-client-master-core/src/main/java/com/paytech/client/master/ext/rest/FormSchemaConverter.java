package com.paytech.client.master.ext.rest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.paytech.client.master.api.onboarding.response.FormSchemaResponseBody;
import com.paytech.client.master.api.onboarding.response.FormSchemaResponseBody.AddressComponent;
import com.paytech.client.master.api.onboarding.response.FormSchemaResponseBody.AddressFormat;
import com.paytech.client.master.api.onboarding.response.FormSchemaResponseBody.CatalogOption;
import com.paytech.client.master.api.onboarding.response.FormSchemaResponseBody.Declaration;
import com.paytech.client.master.api.onboarding.response.FormSchemaResponseBody.Field;
import com.paytech.client.master.api.onboarding.response.FormSchemaResponseBody.Group;
import com.paytech.client.master.api.onboarding.response.FormSchemaResponseBody.IdentifierTypeOption;
import com.paytech.client.master.api.onboarding.response.FormSchemaResponseBody.Material;
import com.paytech.client.master.api.onboarding.response.FormSchemaResponseBody.RelationRules;
import com.paytech.client.master.api.onboarding.response.FormSchemaResponseBody.RelationTypeOption;
import com.paytech.client.master.api.onboarding.response.FormSchemaResponseBody.Section;
import com.paytech.client.master.api.onboarding.response.FormSchemaResponseBody.Validation;
import com.paytech.client.master.core.dal.dataobject.AttributeDefinition;
import com.paytech.client.master.core.dal.dataobject.IdentifierType;
import com.paytech.client.master.core.dal.dataobject.OnboardingSchemaField;
import com.paytech.client.master.domain.enums.ClientType;
import com.paytech.client.master.domain.enums.RequirementLevel;
import com.paytech.client.master.domain.enums.RuleCode;
import com.paytech.client.master.domain.enums.WidgetCode;
import com.paytech.client.master.domain.onboarding.pack.OnboardingPack;
import com.paytech.client.master.domain.onboarding.pack.ReferenceCatalogCodes;
import com.paytech.client.master.domain.onboarding.pack.RuleSettings;
import com.paytech.client.master.domain.onboarding.requirement.MaterialRequirement;

/** 辖区包与程序材料到 CM-2 表单渲染规格的纯组装；文案只给 labelKey。 */
public final class FormSchemaConverter {
    private static final String KEY_PREFIX = "onboardingSchema.";

    /** 仅静态组装。 */
    private FormSchemaConverter() {
    }

    /**
     * 组装渲染规格
     *
     * @param pack 辖区包与 Schema
     * @param partyType 主主体类型
     * @param organizationTypeCode 组织类型（关系人门槛按它解析），可空
     * @param materials 程序材料槽
     * @return 渲染规格
     */
    public static FormSchemaResponseBody convert(OnboardingPack pack, ClientType partyType, String organizationTypeCode,
        List<MaterialRequirement> materials) {
        var body = new FormSchemaResponseBody();
        body.setSchemaId(pack.getSchema().getSchemaId().toString());
        body.setVersionNo(pack.getSchema().getVersionNo());
        body.setJurisdictionCode(pack.jurisdictionCode());
        body.setPartyType(partyType.getCode());
        body.setOrganizationTypeCode(pack.getSchema().getOrganizationTypeCode());
        body.setSections(sections(pack.getFields()));
        body.setCatalogs(catalogs(pack, partyType));
        body.setOrganizationTypes(pack.organizationTypesFor(partyType).stream()
            .map(type -> option(type.getTypeCode(), type.getLabelKey())).toList());
        body.setIdentifierTypes(identifierTypes(pack));
        body.setAddressFormat(addressFormat(pack, pack.getJurisdiction().getAddressFormatCode()));
        body.setFallbackAddressFormat(addressFormat(pack, OnboardingPack.GENERIC_ADDRESS_FORMAT));
        body.setRelationTypes(partyType == ClientType.ORGANIZATION ? relationTypes(pack) : List.of());
        body.setRelationRules(partyType == ClientType.ORGANIZATION ? relationRules(pack, organizationTypeCode) : null);
        body.setMaterials(materials.stream().map(FormSchemaConverter::material).toList());
        body.setDeclarations(pack.getFields().stream().filter(field -> field.getWidgetCode() == WidgetCode.DECLARATION
            && field.getRequirementLevel() != RequirementLevel.HIDDEN).map(field -> declaration(pack, field)).toList());
        return body;
    }

    /** 按分区与分组归并字段，HIDDEN 不返回。 */
    static List<Section> sections(List<OnboardingSchemaField> fields) {
        Map<String, Map<String, List<Field>>> grouped = new LinkedHashMap<>();
        for (OnboardingSchemaField field : fields) {
            if (field.getRequirementLevel() == RequirementLevel.HIDDEN) {
                continue;
            }
            grouped.computeIfAbsent(field.getSection().getCode(), ignored -> new LinkedHashMap<>())
                .computeIfAbsent(field.getDisplayGroup(), ignored -> new ArrayList<>()).add(field(field));
        }
        List<Section> sections = new ArrayList<>();
        grouped.forEach((code, groups) -> {
            var section = new Section();
            section.setSection(code);
            section.setLabelKey(KEY_PREFIX + "section." + code);
            section.setGroups(groups.entrySet().stream().map(entry -> {
                var group = new Group();
                group.setGroup(entry.getKey());
                group.setLabelKey(KEY_PREFIX + "group." + entry.getKey());
                group.setFields(entry.getValue());
                return group;
            }).toList());
            sections.add(section);
        });
        return sections;
    }

    /** 单个字段。 */
    static Field field(OnboardingSchemaField row) {
        var field = new Field();
        field.setFieldKey(row.getFieldKey());
        field.setWidget(row.getWidgetCode().getCode());
        field.setCatalogCode(row.getCatalogCode());
        field.setRequirementLevel(row.getRequirementLevel().getCode());
        field.setRequirementGroup(row.getRequirementGroup());
        field.setRequirementGroupLabelKey(row.getRequirementGroup() == null ? null
            : KEY_PREFIX + "requirementGroup." + row.getRequirementGroup());
        field.setAppliesTo(row.getAppliesToSubject().getCode());
        var validation = new Validation();
        validation.setPattern(row.getValidationPattern());
        validation.setMinLength(row.getMinLength());
        validation.setMaxLength(row.getMaxLength());
        field.setValidation(validation);
        field.setVisibleWhen(row.getVisibleWhen());
        field.setLabelKey(row.getLabelKey());
        field.setHelpKey(row.getHelpKey());
        field.setOrder(row.getDisplayOrder());
        return field;
    }

    /**
     * Schema 字段、地址组件与属性引用的本库目录，组织类型伪目录按机构形态列出
     * <li>REF_ 目录（Platform 参考数据）不放入：字段与地址组件保留 catalogCode，选项由 client-console-bff 向 Platform 取来补全。</li>
     */
    static Map<String, List<CatalogOption>> catalogs(OnboardingPack pack, ClientType partyType) {
        Set<String> codes = new LinkedHashSet<>();
        pack.getFields().stream().map(OnboardingSchemaField::getCatalogCode).filter(Objects::nonNull).forEach(codes::add);
        pack.addressComponents(pack.getJurisdiction().getAddressFormatCode()).forEach(component -> {
            if (component.getCatalogCode() != null) {
                codes.add(component.getCatalogCode());
            }
        });
        pack.getAttributes().values().stream().map(AttributeDefinition::getCatalogCode).filter(Objects::nonNull).forEach(codes::add);
        codes.removeIf(ReferenceCatalogCodes::isReference);
        Map<String, List<CatalogOption>> result = new LinkedHashMap<>();
        for (String code : codes) {
            result.put(code, OnboardingPack.ORGANIZATION_TYPE_CATALOG.equals(code)
                ? pack.organizationTypesFor(ClientType.ORGANIZATION).stream().map(type -> option(type.getTypeCode(), type.getLabelKey())).toList()
                : pack.catalogItems(code, partyType).stream().map(item -> option(item.getItemCode(), item.getLabelKey())).toList());
        }
        return result;
    }

    /** Schema 引用的标识类型。 */
    static List<IdentifierTypeOption> identifierTypes(OnboardingPack pack) {
        List<IdentifierTypeOption> result = new ArrayList<>();
        for (OnboardingSchemaField field : pack.getFields()) {
            IdentifierType type = field.getWidgetCode() == WidgetCode.IDENTIFIER
                ? pack.identifierType(field.getFieldKey().substring("identifier.".length())) : null;
            if (type == null || result.stream().anyMatch(option -> option.getCode().equals(type.getIdentifierTypeCode()))) {
                continue;
            }
            var option = new IdentifierTypeOption();
            option.setCode(type.getIdentifierTypeCode());
            option.setPartyType(type.getPartyType().getCode());
            option.setLabelKey(type.getLabelKey());
            option.setPattern(type.getFormatRegex());
            option.setChecksumRule(type.getChecksumRule());
            option.setIssuerScopeMode(type.getIssuerScopeMode().getCode());
            option.setExpiryRequired(pack.expiryRequired(type.getIdentifierTypeCode()));
            option.setAcquireMode(pack.getRules().value(RuleCode.ACQUIRE_MODE, type.getIdentifierTypeCode()));
            result.add(option);
        }
        return result;
    }

    /** 地址格式及组件（必填已叠加邮编规则）。 */
    static AddressFormat addressFormat(OnboardingPack pack, String code) {
        var format = new AddressFormat();
        format.setCode(code);
        format.setComponents(pack.addressComponents(code).stream().map(row -> {
            var component = new AddressComponent();
            component.setComponent(row.getComponent());
            component.setRequired(pack.componentRequired(code, row));
            component.setCatalogCode(row.getCatalogCode());
            component.setLabelKey(row.getLabelKey());
            component.setOrder(row.getDisplayOrder());
            return component;
        }).toList());
        return format;
    }

    /** 关系类型。 */
    static List<RelationTypeOption> relationTypes(OnboardingPack pack) {
        return pack.getRelationTypes().values().stream().map(type -> {
            var option = new RelationTypeOption();
            option.setCode(type.getRelationTypeCode());
            option.setRuleRole(type.getRuleRole().getCode());
            option.setUboFlag(Boolean.TRUE.equals(type.getUboFlag()));
            option.setOwnershipPctRequired(Boolean.TRUE.equals(type.getOwnershipPctRequired()));
            option.setLabelKey(type.getLabelKey());
            return option;
        }).toList();
    }

    /** 组织关系人门槛。 */
    static RelationRules relationRules(OnboardingPack pack, String organizationTypeCode) {
        RuleSettings rules = pack.getRules();
        String jurisdiction = pack.jurisdictionCode();
        String scope = pack.organizationScope(organizationTypeCode);
        var result = new RelationRules();
        result.setUboThresholdPct(rules.decimal(RuleCode.UBO_THRESHOLD_PCT, jurisdiction).stripTrailingZeros().toPlainString());
        result.setUboFallbackRule(rules.value(RuleCode.UBO_FALLBACK_RULE, jurisdiction));
        result.setControlPersonRequired(rules.flag(RuleCode.CONTROL_PERSON_REQUIRED, jurisdiction));
        result.setMinSignatories(rules.number(RuleCode.MIN_SIGNATORIES, scope));
        result.setRequiresManager(rules.flag(RuleCode.REQUIRES_MANAGER, scope));
        result.setRequiresDirectors(rules.flag(RuleCode.REQUIRES_DIRECTORS, scope));
        result.setUboCollectionMode(rules.value(RuleCode.UBO_COLLECTION_MODE, scope));
        result.setUboThresholdComparison(pack.uboThresholdComparison().getCode());
        return result;
    }

    /** 材料槽。 */
    static Material material(MaterialRequirement requirement) {
        var material = new Material();
        material.setRequirementCode(requirement.requirementCode());
        material.setSubjectRole(requirement.subjectRole());
        material.setDocumentTypes(requirement.documentTypes());
        material.setRequired(requirement.required());
        material.setMaxAgeDays(requirement.maxAgeDays());
        return material;
    }

    /** 声明要求及其文档的当前版本。 */
    static Declaration declaration(OnboardingPack pack, OnboardingSchemaField field) {
        var declaration = new Declaration();
        declaration.setDeclarationType(field.getFieldKey().substring("declaration.".length()));
        declaration.setLabelKey(field.getLabelKey());
        declaration.setRequirementLevel(field.getRequirementLevel().getCode());
        declaration.setDocumentVersion(pack.documentVersion(declaration.getDeclarationType()));
        return declaration;
    }

    /** 目录项。 */
    static CatalogOption option(String code, String labelKey) {
        var option = new CatalogOption();
        option.setCode(code);
        option.setLabelKey(labelKey);
        return option;
    }
}
