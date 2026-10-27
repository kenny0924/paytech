package com.paytech.client.master.domain.onboarding.pack;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.paytech.client.master.core.dal.dataobject.AddressFormatComponent;
import com.paytech.client.master.core.dal.dataobject.AttributeDefinition;
import com.paytech.client.master.core.dal.dataobject.CatalogItem;
import com.paytech.client.master.core.dal.dataobject.IdentifierType;
import com.paytech.client.master.core.dal.dataobject.Jurisdiction;
import com.paytech.client.master.core.dal.dataobject.OnboardingSchema;
import com.paytech.client.master.core.dal.dataobject.OnboardingSchemaField;
import com.paytech.client.master.core.dal.dataobject.OrganizationType;
import com.paytech.client.master.core.dal.dataobject.RelationType;
import com.paytech.client.master.domain.enums.CatalogStatus;
import com.paytech.client.master.domain.enums.ClientType;
import com.paytech.client.master.domain.enums.RuleCode;
import com.paytech.client.master.domain.enums.SchemaSubject;

import lombok.Getter;

/**
 * 一个辖区包在一次请求中的完整只读快照：辖区、规则开关、目录与申请冻结的采集 Schema
 * <li>由 OnboardingPackService 一次装载；只含纯计算，不访问数据库或外部服务。</li>
 * <li>目录与规则全部来自 DML：代码按编码解释，不按辖区、组织类型或标识类型取值分支。</li>
 */
@Getter
public class OnboardingPack {
    /** 境外地址或辖区未配置地址格式时使用的通用格式。 */
    public static final String GENERIC_ADDRESS_FORMAT = "GENERIC";
    /** 组织类型以伪目录代码出现在 SELECT 字段与 form-schema 的 catalogs 中。 */
    public static final String ORGANIZATION_TYPE_CATALOG = "ORGANIZATION_TYPE";
    /** 全局目录项与默认 Schema 的作用域代码。 */
    public static final String ANY = "*";

    /** 辖区包。 */
    private final Jurisdiction jurisdiction;
    /** 规则开关解释器。 */
    private final RuleSettings rules;
    /** 辖区全部 ACTIVE 组织类型。 */
    private final List<OrganizationType> organizationTypes;
    /** 辖区与全局 ACTIVE 标识类型，按代码。 */
    private final Map<String, IdentifierType> identifierTypes;
    /** 辖区与全局 ACTIVE 关系类型，按代码。 */
    private final Map<String, RelationType> relationTypes;
    /** 地址格式到组件（按展示顺序）。 */
    private final Map<String, List<AddressFormatComponent>> addressFormats;
    /** 目录代码到辖区与全局 ACTIVE 目录项。 */
    private final Map<String, List<CatalogItem>> catalogs;
    /** 辖区与全局 ACTIVE 属性定义，按代码。 */
    private final Map<String, AttributeDefinition> attributes;
    /** 申请冻结的 Schema 版本。 */
    private final OnboardingSchema schema;
    /** 该版本全部字段（按展示顺序）。 */
    private final List<OnboardingSchemaField> fields;

    /** 只接受已过滤为 ACTIVE 的目录行。 */
    public OnboardingPack(Jurisdiction jurisdiction, RuleSettings rules, List<OrganizationType> organizationTypes,
        List<IdentifierType> identifierTypes, List<RelationType> relationTypes, List<AddressFormatComponent> components,
        List<CatalogItem> catalogItems, List<AttributeDefinition> attributes, OnboardingSchema schema,
        List<OnboardingSchemaField> fields) {
        this.jurisdiction = jurisdiction;
        this.rules = rules;
        this.organizationTypes = List.copyOf(organizationTypes);
        this.identifierTypes = index(identifierTypes, IdentifierType::getIdentifierTypeCode);
        this.relationTypes = index(relationTypes, RelationType::getRelationTypeCode);
        this.addressFormats = components.stream().collect(Collectors.groupingBy(AddressFormatComponent::getAddressFormatCode,
            LinkedHashMap::new, Collectors.toList()));
        this.catalogs = catalogItems.stream().collect(Collectors.groupingBy(CatalogItem::getCatalogCode, LinkedHashMap::new,
            Collectors.toList()));
        this.attributes = index(attributes, AttributeDefinition::getAttributeCode);
        this.schema = schema;
        this.fields = List.copyOf(fields);
    }

    /** 辖区代码。 */
    public String jurisdictionCode() {
        return jurisdiction.getJurisdictionCode();
    }

    /** 组织类型规则的作用对象代码：辖区码:类型码。 */
    public String organizationScope(String organizationTypeCode) {
        return organizationTypeCode == null ? null : jurisdictionCode() + ":" + organizationTypeCode;
    }

    /** 地址所在国家等于辖区国家（或未填）时用辖区本地格式，否则用 GENERIC。 */
    public String addressFormatFor(String countryCode) {
        return countryCode == null || countryCode.equals(jurisdiction.getCountryCode()) ? jurisdiction.getAddressFormatCode()
            : GENERIC_ADDRESS_FORMAT;
    }

    /** 某地址格式的组件；未配置时为空列表。 */
    public List<AddressFormatComponent> addressComponents(String formatCode) {
        return addressFormats.getOrDefault(formatCode, List.of());
    }

    /** 邮编是否必填：辖区本地格式下 POSTAL_CODE_REQUIRED 显式配置时覆盖组件标记。 */
    public boolean componentRequired(String formatCode, AddressFormatComponent component) {
        boolean local = formatCode.equals(jurisdiction.getAddressFormatCode());
        if (local && "postalCode".equals(component.getComponent())
            && rules.configured(RuleCode.POSTAL_CODE_REQUIRED, jurisdictionCode())) {
            return rules.flag(RuleCode.POSTAL_CODE_REQUIRED, jurisdictionCode());
        }
        return Boolean.TRUE.equals(component.getRequiredFlag());
    }

    /** 申请主主体类型可选的组织类型（按 PARTY_TYPE 规则）。 */
    public List<OrganizationType> organizationTypesFor(ClientType partyType) {
        return organizationTypes.stream()
            .filter(type -> partyType.getCode().equals(rules.value(RuleCode.PARTY_TYPE, organizationScope(type.getTypeCode()))))
            .toList();
    }

    /** 目录项可用于该主体类型（client_types 为空表示都适用）。 */
    public List<CatalogItem> catalogItems(String catalogCode, ClientType partyType) {
        return catalogs.getOrDefault(catalogCode, List.of()).stream()
            .filter(item -> item.getClientTypes() == null || Arrays.asList(item.getClientTypes().split(",")).contains(partyType.getCode()))
            .toList();
    }

    /** 取值是否在目录中；组织类型伪目录按 PARTY_TYPE 过滤。 */
    public boolean catalogContains(String catalogCode, String value, ClientType partyType) {
        if (ORGANIZATION_TYPE_CATALOG.equals(catalogCode)) {
            return organizationTypesFor(ClientType.ORGANIZATION).stream().anyMatch(type -> type.getTypeCode().equals(value));
        }
        return catalogItems(catalogCode, partyType).stream().anyMatch(item -> item.getItemCode().equals(value));
    }

    /** 适用于该主体角色、且不隐藏的字段。 */
    public List<OnboardingSchemaField> fieldsFor(SchemaSubject subject) {
        return fields.stream()
            .filter(field -> field.getAppliesToSubject() == subject || field.getAppliesToSubject() == SchemaSubject.BOTH)
            .toList();
    }

    /** 字段键前缀为 prefix 的字段（如 declaration.、identifier.）。 */
    public List<OnboardingSchemaField> fieldsWithPrefix(String prefix) {
        return fields.stream().filter(field -> field.getFieldKey().startsWith(prefix)).toList();
    }

    /** 标识类型；未在本包为 null。 */
    public IdentifierType identifierType(String code) {
        return code == null ? null : identifierTypes.get(code);
    }

    /** 关系类型；未在本包为 null。 */
    public RelationType relationType(String code) {
        return code == null ? null : relationTypes.get(code);
    }

    /** 属性定义；未在本包为 null。 */
    public AttributeDefinition attribute(String code) {
        return code == null ? null : attributes.get(code);
    }

    /** 标识号码是否只保存 Vault Token。 */
    public boolean vaultRequired(String identifierTypeCode) {
        return rules.flag(RuleCode.VAULT_REQUIRED, identifierTypeCode);
    }

    /** 标识是否必须填写到期日。 */
    public boolean expiryRequired(String identifierTypeCode) {
        return rules.flag(RuleCode.EXPIRY_REQUIRED, identifierTypeCode);
    }

    /** 尽调档次：准入策略 ENHANCED_ONLY 时强制 ENHANCED，否则 STANDARD（自动评级后续另立需求）。 */
    public String tier() {
        return "ENHANCED_ONLY".equals(rules.value(RuleCode.ACCEPTANCE_POLICY, jurisdictionCode())) ? "ENHANCED" : "STANDARD";
    }

    /** 只保留 ACTIVE 行并按键建索引。 */
    private static <T> Map<String, T> index(List<T> rows, Function<T, String> key) {
        Map<String, T> result = new LinkedHashMap<>();
        rows.forEach(row -> result.put(key.apply(row), row));
        return result;
    }

    /** ACTIVE 判定，供装载方过滤。 */
    public static boolean active(CatalogStatus status) {
        return Objects.equals(status, CatalogStatus.ACTIVE);
    }
}
