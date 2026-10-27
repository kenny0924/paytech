package com.paytech.client.master.service.processor;

import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.INVALID_DRAFT;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.paytech.client.master.core.dal.dataobject.AttributeDefinition;
import com.paytech.client.master.core.dal.dataobject.ClientDraftFields;
import com.paytech.client.master.core.dal.dataobject.ClientDraftIdentity;
import com.paytech.client.master.core.dal.dataobject.IdentifierType;
import com.paytech.client.master.core.dal.dataobject.OnboardingSchema;
import com.paytech.client.master.domain.enums.AttributeSensitivity;
import com.paytech.client.master.domain.enums.ClientType;
import com.paytech.client.master.domain.onboarding.ClientDraftChanges;
import com.paytech.client.master.domain.onboarding.ClientDraftChanges.AttributeValue;
import com.paytech.client.master.domain.onboarding.ClientDraftPatch;
import com.paytech.client.master.domain.onboarding.pack.IdentifierRules;
import com.paytech.client.master.domain.onboarding.pack.OnboardingPack;
import com.paytech.client.master.domainservice.service.OnboardingPackService;
import com.paytech.client.master.integration.ClientIdentifierVaultClient;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;

import lombok.AllArgsConstructor;

/**
 * 草稿与人员写入共用的事务外准备：按申请冻结的辖区包校验并转换标识、扩展属性、地址格式与组织类型
 * <li>证件号在这里规范化、检查格式与校验位，VAULT_REQUIRED 类型立即 Protect（STABLE）；之后的写事务只保存 Token、脱敏值与检查结论。</li>
 * <li>只读本库目录并调用 Vault，必须在数据库事务外执行；任何字段问题一次性以 INVALID_DRAFT 加 errors[] 拒绝，且不调用 Vault。</li>
 */
@AllArgsConstructor
public class ClientDraftPreparer {
    private static final String IDENTIFIER_TYPE = "identifier.identifierType";
    private static final String ORGANIZATION_TYPE = "organization.organizationType";
    private static final String ADDRESS_FORMAT = "address.addressFormatCode";

    private final OnboardingPackService packs;
    private final ClientIdentifierVaultClient identifiers;

    /**
     * 准备一次写入
     *
     * @param application 已核对归属与可编辑的申请
     * @param current 被修改主体的当前资料
     * @param patch 已规范化的字段修改
     * @param subjectType 被修改主体的类型（关联人员恒为 INDIVIDUAL）
     * @param primary 是否修改申请主主体；组织类型只能由主主体修改
     * @return 可直接在短事务中保存的写入
     */
    public ClientDraftChanges prepare(ClientDraftIdentity application, ClientDraftFields current, ClientDraftPatch patch,
        ClientType subjectType, boolean primary) {
        OnboardingPack pack = packs.load(application.schemaId());
        List<ProblemFieldError> errors = new ArrayList<>();
        Map<String, Object> columns = new HashMap<>(patch.databaseValues());
        Map<String, AttributeValue> attributes = attributes(pack, patch, subjectType, errors);
        PendingIdentifier pending = identifier(pack, current, patch, subjectType, columns, errors);
        addressFormat(pack, patch, errors);
        OnboardingSchema schema = primary ? organizationType(pack, application, patch, errors) : null;
        if (!errors.isEmpty()) {
            throw new RequestBodyValidationException(INVALID_DRAFT, errors, null);
        }
        if (pending != null) {
            protect(pack, pending, columns);
        }
        if (patch.touches("address")) {
            String country = patch.values().containsKey("address.countryCode") ? patch.values().get("address.countryCode")
                : current.addressCountryCode();
            columns.put("address_addressFormatCode", pack.addressFormatFor(country));
        }
        return new ClientDraftChanges(columns, attributes, patch.touches("identifier"),
            schema == null ? null : patch.values().get(ORGANIZATION_TYPE), schema == null ? null : schema.getSchemaId());
    }

    /** 扩展属性：定义须属于本辖区包、适用该主体类型且不是 VAULT 级；按数据类型转换。 */
    Map<String, AttributeValue> attributes(OnboardingPack pack, ClientDraftPatch patch, ClientType subjectType,
        List<ProblemFieldError> errors) {
        Map<String, AttributeValue> result = new LinkedHashMap<>();
        patch.attributeValues().forEach((code, value) -> {
            AttributeDefinition definition = pack.attribute(code);
            String path = ClientDraftPatch.ATTRIBUTES + code;
            if (definition == null || definition.getPartyType() != subjectType
                || definition.getSensitivity() == AttributeSensitivity.VAULT) {
                errors.add(new ProblemFieldError(path, "当前辖区不支持此属性"));
                return;
            }
            AttributeValue converted = value == null ? null : attributeValue(definition, value);
            if (value != null && converted == null) {
                errors.add(new ProblemFieldError(path, "属性取值不符合约定"));
                return;
            }
            result.put(code, converted);
        });
        return result;
    }

    /** 按属性数据类型转换；不合规返回 null。 */
    AttributeValue attributeValue(AttributeDefinition definition, String value) {
        try {
            return switch (definition.getDataType()) {
                case DATE -> new AttributeValue(null, LocalDate.parse(value), null);
                case DECIMAL -> value.matches("-?[0-9]{1,16}(\\.[0-9]{1,8})?") ? new AttributeValue(null, null, new BigDecimal(value))
                    : null;
                case COUNTRY -> value.toUpperCase(java.util.Locale.ROOT).matches("[A-Z]{2}")
                    ? new AttributeValue(value.toUpperCase(java.util.Locale.ROOT), null, null) : null;
                case CODE -> value.matches("[A-Za-z0-9_-]{1,64}") ? new AttributeValue(value, null, null) : null;
                case TEXT -> value.codePointCount(0, value.length()) <= (definition.getMaxLength() == null ? 255
                    : definition.getMaxLength()) ? new AttributeValue(value, null, null) : null;
            };
        } catch (DateTimeException invalid) {
            return null;
        }
    }

    /**
     * 页面随地址组件提交的地址格式码：须是本申请 form-schema 下发的辖区本地格式或境外通用格式
     * <li>只做一致性校验；落库值始终由服务端按地址国家推导（境外地址为 GENERIC），不采用页面取值。</li>
     */
    static void addressFormat(OnboardingPack pack, ClientDraftPatch patch, List<ProblemFieldError> errors) {
        String format = patch.values().get(ADDRESS_FORMAT);
        if (format != null && !format.equals(pack.addressFormatFor(null)) && !OnboardingPack.GENERIC_ADDRESS_FORMAT.equals(format)) {
            errors.add(new ProblemFieldError(ADDRESS_FORMAT, "地址格式与申请辖区不符"));
        }
    }

    /**
     * 标识：号码修改时按标识类型规范化与检查；只改类型时清空旧号码，要求重新填写（不 Reveal 回显旧号码）
     *
     * @return 待 Protect 或写入的号码；号码未修改或已清空时为 null
     */
    PendingIdentifier identifier(OnboardingPack pack, ClientDraftFields current, ClientDraftPatch patch, ClientType subjectType,
        Map<String, Object> columns, List<ProblemFieldError> errors) {
        Map<String, String> values = patch.values();
        boolean typeDirty = values.containsKey(IDENTIFIER_TYPE);
        String type = typeDirty ? values.get(IDENTIFIER_TYPE) : current.identifierIdentifierType();
        IdentifierType definition = pack.identifierType(type);
        if (typeDirty && type != null && (definition == null || definition.getPartyType() != subjectType)) {
            errors.add(new ProblemFieldError(IDENTIFIER_TYPE, "当前辖区不支持此证件类型"));
            return null;
        }
        if (!values.containsKey(ClientDraftPatch.IDENTIFIER_VALUE)) {
            if (typeDirty && !Objects.equals(type, current.identifierIdentifierType())) {
                clearIdentifierValue(columns);
            }
            return null;
        }
        String raw = values.get(ClientDraftPatch.IDENTIFIER_VALUE);
        if (raw == null) {
            clearIdentifierValue(columns);
            return null;
        }
        if (definition == null || definition.getPartyType() != subjectType) {
            errors.add(new ProblemFieldError(IDENTIFIER_TYPE, "请先选择证件类型"));
            return null;
        }
        String normalized = IdentifierRules.normalize(definition, raw);
        if (normalized == null) {
            errors.add(new ProblemFieldError(ClientDraftPatch.IDENTIFIER_VALUE, "证件号码只能包含字母、数字及常见分隔符"));
            return null;
        }
        return new PendingIdentifier(definition, normalized);
    }

    /** VAULT_REQUIRED 类型只保存 Token，其他类型保存规范化公开登记号；都写末 4 位脱敏值与检查结论。 */
    void protect(OnboardingPack pack, PendingIdentifier pending, Map<String, Object> columns) {
        String code = pending.type().getIdentifierTypeCode();
        boolean vault = pack.vaultRequired(code);
        columns.put("identifier_identifierVaultToken", vault ? identifiers.protect(pending.normalized()) : null);
        columns.put("identifier_publicIdentifierValue", vault ? null : pending.normalized());
        columns.put("identifier_identifierMaskedValue", IdentifierRules.mask(pending.normalized()));
        columns.put("identifier_identifierCheckResult", IdentifierRules.check(pending.type(), pending.normalized()));
    }

    /** 清空已保存号码及其派生值。 */
    static void clearIdentifierValue(Map<String, Object> columns) {
        columns.put("identifier_identifierVaultToken", null);
        columns.put("identifier_publicIdentifierValue", null);
        columns.put("identifier_identifierMaskedValue", null);
        columns.put("identifier_identifierCheckResult", null);
    }

    /**
     * 组织主主体更换组织类型：新类型须为本辖区 ACTIVE 机构组织类型，并按其重新解析 Schema
     *
     * @return 新 Schema；未更换时为 null
     */
    OnboardingSchema organizationType(OnboardingPack pack, ClientDraftIdentity application, ClientDraftPatch patch,
        List<ProblemFieldError> errors) {
        if (application.clientType() != ClientType.ORGANIZATION || !patch.values().containsKey(ORGANIZATION_TYPE)) {
            return null;
        }
        String type = patch.values().get(ORGANIZATION_TYPE);
        if (Objects.equals(type, application.organizationTypeCode())) {
            return null;
        }
        if (type != null && !pack.catalogContains(OnboardingPack.ORGANIZATION_TYPE_CATALOG, type, ClientType.ORGANIZATION)) {
            errors.add(new ProblemFieldError(ORGANIZATION_TYPE, "当前辖区不支持此组织类型"));
            return null;
        }
        OnboardingSchema schema = packs.resolveSchema(pack.jurisdictionCode(), ClientType.ORGANIZATION, type);
        if (schema == null) {
            errors.add(new ProblemFieldError(ORGANIZATION_TYPE, "当前辖区暂不受理此组织类型"));
        }
        return schema;
    }

    /**
     * 已规范化、待保存的号码；只在内存中短暂存在
     *
     * @param type 标识类型
     * @param normalized 规范化号码
     */
    record PendingIdentifier(IdentifierType type, String normalized) {
        /** 含号码原值，不输出到日志。 */
        @Override
        public String toString() {
            return "PendingIdentifier[redacted]";
        }
    }
}
