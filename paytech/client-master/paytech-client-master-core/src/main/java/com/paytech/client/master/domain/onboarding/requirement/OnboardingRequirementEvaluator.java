package com.paytech.client.master.domain.onboarding.requirement;

import static com.paytech.client.master.api.enums.OnboardingRequirementCode.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.paytech.client.master.api.enums.OnboardingAwaitingParty;
import com.paytech.client.master.api.enums.OnboardingRemediationCode;
import com.paytech.client.master.api.enums.OnboardingRequirementCode;
import com.paytech.client.master.api.enums.OnboardingRequirementKind;
import com.paytech.client.master.api.enums.OnboardingRequirementStatus;
import com.paytech.client.master.core.dal.dataobject.AddressFormatComponent;
import com.paytech.client.master.core.dal.dataobject.IdentifierType;
import com.paytech.client.master.core.dal.dataobject.OnboardingSchemaField;
import com.paytech.client.master.domain.enums.ClientApplicationStatus;
import com.paytech.client.master.domain.enums.ClientType;
import com.paytech.client.master.domain.enums.RequirementLevel;
import com.paytech.client.master.domain.enums.RuleCode;
import com.paytech.client.master.domain.enums.SchemaSubject;
import com.paytech.client.master.domain.enums.WidgetCode;
import com.paytech.client.master.domain.onboarding.pack.IdentifierRules;
import com.paytech.client.master.domain.onboarding.pack.OnboardingPack;

/**
 * Schema 驱动的需求清单计算（需求 B2、B3，设计第 8 节）：提交校验与 GET requirements 同源
 * <li>顺序：Schema 字段（格式、必填、目录、二选一组、地址组件）→ 标识（格式、校验位、到期、作用域、冲突）→
 * 关系门槛（规则开关）→ 材料（程序解析）→ 声明。真伪判断不在这里。</li>
 * <li>纯计算：不访问数据库或外部服务；所有国家差异来自辖区包数据，不按辖区、组织类型或标识类型取值分支。</li>
 */
public final class OnboardingRequirementEvaluator {
    /** 关联人员材料的通配适用角色。 */
    private static final String ANY_RELATED = "ANY_RELATED";
    /** 主主体材料的适用角色。 */
    private static final String PRIMARY = "PRIMARY";

    private final RequirementInput input;
    private final OnboardingPack pack;
    private final LocalDate today;
    private final List<OnboardingRequirement> items = new ArrayList<>();

    /** 只持有本次输入。 */
    private OnboardingRequirementEvaluator(RequirementInput input) {
        this.input = input;
        this.pack = input.getPack();
        this.today = LocalDate.ofInstant(input.getNow(), ZoneOffset.UTC);
    }

    /**
     * 计算需求清单
     *
     * @param input 申请全部提交事实
     * @return 清单项：未满足项带错误码与补救动作，已满足项为 SATISFIED 或 PENDING_VERIFICATION
     */
    public static List<OnboardingRequirement> evaluate(RequirementInput input) {
        var evaluator = new OnboardingRequirementEvaluator(input);
        for (RequirementSubject subject : input.getSubjects()) {
            evaluator.subjectFields(subject);
        }
        RequirementSubject primary = input.getSubjects().getFirst();
        if (primary.partyType() == ClientType.ORGANIZATION) {
            evaluator.relations(primary);
        }
        evaluator.materials();
        evaluator.declarations(primary);
        return evaluator.finish();
    }

    /** 一个主体的 Schema 字段：普通字段、地址、标识与二选一组。 */
    void subjectFields(RequirementSubject subject) {
        Map<String, List<FieldResult>> groups = new LinkedHashMap<>();
        for (OnboardingSchemaField field : pack.fieldsFor(subject.role())) {
            if (field.getRequirementLevel() == RequirementLevel.HIDDEN || !visible(field, subject)) {
                continue;
            }
            FieldResult result = field(subject, field);
            if (result == null) {
                continue;
            }
            if (field.getRequirementGroup() != null) {
                groups.computeIfAbsent(field.getRequirementGroup(), ignored -> new ArrayList<>()).add(result);
            } else {
                record(subject, field.getFieldKey(), result);
            }
        }
        groups.forEach((group, results) -> recordGroup(subject, group, results));
        IdentifierFact fact = subject.identifier();
        if (fact.type() != null && fact.valuePresent() && !identifierAllowed(subject, fact.type())) {
            record(subject, "identifier.identifierType", FieldResult.error(FIELD_VALUE_NOT_IN_CATALOG, OnboardingRequirementKind.IDENTIFIER));
        }
    }

    /** 单个字段的结论；由其他步骤计算的控件返回 null。 */
    FieldResult field(RequirementSubject subject, OnboardingSchemaField field) {
        return switch (field.getWidgetCode()) {
            case PERSON_LIST, DOCUMENT_SLOT, DECLARATION -> null;
            case ADDRESS -> {
                address(subject, field);
                yield null;
            }
            case IDENTIFIER -> identifier(subject, field);
            default -> valueField(subject, field);
        };
    }

    /** 普通取值字段：缺失、格式、目录与日期规则。 */
    FieldResult valueField(RequirementSubject subject, OnboardingSchemaField field) {
        String value = subject.values().get(field.getFieldKey());
        if (value == null) {
            return FieldResult.missing(field.getRequirementLevel(), OnboardingRequirementKind.FIELD);
        }
        OnboardingRequirementCode error = format(field, value);
        if (error == null && field.getCatalogCode() != null
            && !pack.catalogContains(field.getCatalogCode(), value, subject.partyType())) {
            error = FIELD_VALUE_NOT_IN_CATALOG;
        }
        if (error == null) {
            error = dateRule(field.getFieldKey(), value);
        }
        return error == null ? FieldResult.satisfied(OnboardingRequirementKind.FIELD)
            : FieldResult.error(error, OnboardingRequirementKind.FIELD);
    }

    /** 正则、长度、金额与属性定义的格式。 */
    OnboardingRequirementCode format(OnboardingSchemaField field, String value) {
        int length = value.codePointCount(0, value.length());
        if ((field.getMinLength() != null && length < field.getMinLength())
            || (field.getMaxLength() != null && length > field.getMaxLength())
            || (field.getValidationPattern() != null && !value.matches(field.getValidationPattern()))) {
            return FIELD_FORMAT_INVALID;
        }
        if (field.getWidgetCode() == WidgetCode.MONEY && new BigDecimal(value).signum() <= 0) {
            return FIELD_FORMAT_INVALID;
        }
        var attribute = field.getFieldKey().startsWith("attributes.") ? pack.attribute(field.getFieldKey().substring(11)) : null;
        if (attribute != null && attribute.getFormatRegex() != null && !value.matches(attribute.getFormatRegex())) {
            return FIELD_FORMAT_INVALID;
        }
        return null;
    }

    /** 出生日期最低年龄（MIN_AGE 规则）与执照到期日。 */
    OnboardingRequirementCode dateRule(String fieldKey, String value) {
        if ("individual.birthDate".equals(fieldKey)) {
            int minAge = pack.getRules().number(RuleCode.MIN_AGE, pack.jurisdictionCode());
            return Period.between(LocalDate.parse(value), today).getYears() < minAge ? DOB_AGE_UNDER_MINIMUM : null;
        }
        if ("organization.licenseExpiryDate".equals(fieldKey) && LocalDate.parse(value).isBefore(today)) {
            return IDENTIFIER_EXPIRED;
        }
        return null;
    }

    /** 地址：按地址所在国家选择辖区格式或 GENERIC，逐组件校验必填与目录。 */
    void address(RequirementSubject subject, OnboardingSchemaField field) {
        String format = pack.addressFormatFor(subject.values().get("address.countryCode"));
        for (AddressFormatComponent component : pack.addressComponents(format)) {
            String key = "address." + component.getComponent();
            String value = subject.values().get(key);
            FieldResult result;
            if (value == null) {
                result = pack.componentRequired(format, component)
                    ? FieldResult.missing(field.getRequirementLevel(), OnboardingRequirementKind.FIELD) : null;
            } else if (component.getCatalogCode() != null
                && !pack.catalogContains(component.getCatalogCode(), value, subject.partyType())) {
                result = FieldResult.error(FIELD_VALUE_NOT_IN_CATALOG, OnboardingRequirementKind.FIELD);
            } else {
                result = FieldResult.satisfied(OnboardingRequirementKind.FIELD);
            }
            if (result != null) {
                record(subject, key, result);
            }
        }
    }

    /**
     * 标识字段 identifier.<类型>：只有主体所选类型等于该字段类型时才可能满足
     * <li>所选类型不在本主体任何标识字段中时，单独报告类型不在目录。</li>
     */
    FieldResult identifier(RequirementSubject subject, OnboardingSchemaField field) {
        String expected = field.getFieldKey().substring("identifier.".length());
        IdentifierFact fact = subject.identifier();
        if (fact.type() == null || !fact.type().equals(expected) || !fact.valuePresent()) {
            return FieldResult.missing(field.getRequirementLevel(), OnboardingRequirementKind.IDENTIFIER);
        }
        OnboardingRequirementCode error = identifierError(fact);
        return error == null ? FieldResult.satisfied(OnboardingRequirementKind.IDENTIFIER)
            : FieldResult.error(error, OnboardingRequirementKind.IDENTIFIER);
    }

    /** 所选标识类型是否出现在本主体适用的标识字段中；本主体没有任何标识字段时不限制。 */
    boolean identifierAllowed(RequirementSubject subject, String type) {
        var identifiers = pack.fieldsFor(subject.role()).stream().filter(field -> field.getWidgetCode() == WidgetCode.IDENTIFIER
            && field.getRequirementLevel() != RequirementLevel.HIDDEN).toList();
        return identifiers.isEmpty() || identifiers.stream().anyMatch(field -> field.getFieldKey().equals("identifier." + type));
    }

    /** 已选类型且有号码时的格式、校验位、作用域、到期与冲突。 */
    OnboardingRequirementCode identifierError(IdentifierFact fact) {
        IdentifierType type = pack.identifierType(fact.type());
        if (type == null) {
            return FIELD_VALUE_NOT_IN_CATALOG;
        }
        if (fact.checkResult() != null) {
            return IdentifierRules.CHECKSUM_FAILED.equals(fact.checkResult()) ? IDENTIFIER_CHECKSUM_FAILED : IDENTIFIER_FORMAT_INVALID;
        }
        if (fact.scopeMissing() || (pack.expiryRequired(fact.type()) && fact.expiryDate() == null)) {
            return FIELD_MISSING;
        }
        if (fact.expiryDate() != null && fact.expiryDate().isBefore(today)) {
            return IDENTIFIER_EXPIRED;
        }
        return fact.conflict() ? IDENTIFIER_CONFLICT : null;
    }

    /** 二选一组：任一成员满足即满足，合并为 group.<组> 一项；成员自身的格式错误单独报告。 */
    void recordGroup(RequirementSubject subject, String group, List<FieldResult> results) {
        if (results.stream().anyMatch(FieldResult::isSatisfied)) {
            record(subject, "group." + group, results.stream().filter(FieldResult::isSatisfied).findFirst().orElseThrow());
            return;
        }
        FieldResult invalid = results.stream().filter(result -> result.error() != null && result.error() != FIELD_MISSING)
            .findFirst().orElse(null);
        if (invalid != null) {
            record(subject, "group." + group, invalid);
            return;
        }
        RequirementLevel level = results.stream().map(FieldResult::level).filter(Objects::nonNull)
            .reduce((left, right) -> left == RequirementLevel.REQUIRED || right == RequirementLevel.REQUIRED
                ? RequirementLevel.REQUIRED : left).orElse(RequirementLevel.OPTIONAL);
        FieldResult missing = FieldResult.missing(level, results.getFirst().kind());
        if (missing != null) {
            record(subject, "group." + group, missing);
        }
    }

    /** 可见条件 field_key=value；格式不符的条件视为始终可见。 */
    boolean visible(OnboardingSchemaField field, RequirementSubject subject) {
        String condition = field.getVisibleWhen();
        if (condition == null || !condition.contains("=")) {
            return true;
        }
        String[] parts = condition.split("=", 2);
        return parts[1].strip().equals(subject.values().get(parts[0].strip()));
    }

    /** 组织关系人门槛，由规则开关驱动。 */
    void relations(RequirementSubject primary) {
        RelationRules rules = new RelationRules(pack, input.getOrganizationTypeCode(), input.getRelations());
        if (input.getSubjects().size() < 2 && pack.fieldsFor(SchemaSubject.PRIMARY).stream()
            .anyMatch(field -> field.getWidgetCode() == WidgetCode.PERSON_LIST && field.getRequirementLevel() == RequirementLevel.REQUIRED)) {
            add(OnboardingRequirementKind.PERSON, "people", primary.partyId(), RELATED_PERSON_INCOMPLETE);
        }
        rules.violations().forEach((key, code) -> add(OnboardingRequirementKind.RELATION, key, primary.partyId(), code));
    }

    /** 程序材料要求：逐主体按适用角色计算；范围外人员的材料须先移除。 */
    void materials() {
        var inScope = input.getSubjects().stream().map(RequirementSubject::partyId).toList();
        for (DocumentFact document : input.getDocuments()) {
            if (!inScope.contains(document.subjectPartyId())) {
                add(OnboardingRequirementKind.DOCUMENT, "documents[" + document.documentId() + "]", document.subjectPartyId(),
                    DOCUMENT_SUBJECT_REMOVED);
            }
        }
        for (RequirementSubject subject : input.getSubjects()) {
            for (MaterialRequirement material : input.getMaterials()) {
                if (material.required() && applies(material, subject)) {
                    material(subject, material);
                }
            }
        }
    }

    /** 材料要求是否适用于该主体。 */
    boolean applies(MaterialRequirement material, RequirementSubject subject) {
        if (subject.role() == SchemaSubject.PRIMARY) {
            return PRIMARY.equals(material.subjectRole());
        }
        return ANY_RELATED.equals(material.subjectRole())
            || subject.roles().stream().anyMatch(role -> role.getCode().equals(material.subjectRole()));
    }

    /** 一项必交材料：任一份合规即满足，否则报告第一份的问题或缺失。 */
    void material(RequirementSubject subject, MaterialRequirement material) {
        String key = "document." + material.requirementCode();
        OnboardingRequirementCode firstError = DOCUMENT_MISSING;
        for (DocumentFact document : input.getDocuments()) {
            if (document.subjectPartyId() != subject.partyId() || !document.requirementCode().equals(material.requirementCode())) {
                continue;
            }
            OnboardingRequirementCode error = documentError(material, document);
            if (error == null) {
                record(subject, key, FieldResult.satisfied(OnboardingRequirementKind.DOCUMENT));
                return;
            }
            firstError = firstError == DOCUMENT_MISSING ? error : firstError;
        }
        add(OnboardingRequirementKind.DOCUMENT, key, subject.partyId(), firstError);
    }

    /** 单份材料的类型、可用性、到期与时效。 */
    OnboardingRequirementCode documentError(MaterialRequirement material, DocumentFact document) {
        if (!material.documentTypes().isEmpty() && !material.documentTypes().contains(document.documentType())) {
            return DOCUMENT_TYPE_INVALID;
        }
        if (!document.usable()) {
            return DOCUMENT_UNREADABLE;
        }
        if (document.expiryDate() != null && document.expiryDate().isBefore(today)) {
            return DOCUMENT_EXPIRED;
        }
        if (material.maxAgeDays() != null && document.issueDate() != null
            && document.issueDate().plusDays(material.maxAgeDays()).isBefore(today)) {
            return DOCUMENT_TOO_OLD;
        }
        return null;
    }

    /** Schema 声明字段：最新取值须为 TRUE。 */
    void declarations(RequirementSubject primary) {
        for (OnboardingSchemaField field : pack.fieldsFor(SchemaSubject.PRIMARY)) {
            if (field.getWidgetCode() != WidgetCode.DECLARATION || field.getRequirementLevel() == RequirementLevel.HIDDEN
                || field.getRequirementLevel() == RequirementLevel.OPTIONAL) {
                continue;
            }
            String type = field.getFieldKey().substring("declaration.".length());
            FieldResult result = "TRUE".equals(input.getDeclarations().get(type))
                ? FieldResult.satisfied(OnboardingRequirementKind.DECLARATION)
                : FieldResult.error(DECLARATION_MISSING, OnboardingRequirementKind.DECLARATION).withLevel(field.getRequirementLevel());
            record(primary, field.getFieldKey(), result);
        }
    }

    /** 记录一项字段结论；可选字段缺失不形成清单项。 */
    void record(RequirementSubject subject, String key, FieldResult result) {
        if (result == null) {
            return;
        }
        if (result.isSatisfied()) {
            items.add(new OnboardingRequirement(result.kind(), key, subject.partyId(), OnboardingRequirementStatus.SATISFIED, null,
                null, null, null));
            return;
        }
        boolean eventually = result.error() == FIELD_MISSING || result.error() == DECLARATION_MISSING
            ? result.level() == RequirementLevel.EVENTUALLY : false;
        items.add(new OnboardingRequirement(result.kind(), key, subject.partyId(),
            eventually ? OnboardingRequirementStatus.EVENTUALLY_DUE : OnboardingRequirementStatus.CURRENTLY_DUE,
            OnboardingAwaitingParty.CLIENT, result.error(), remediation(result.error()), null));
    }

    /** 记录一项提交前必须处理的问题。 */
    void add(OnboardingRequirementKind kind, String key, long partyId, OnboardingRequirementCode code) {
        items.add(new OnboardingRequirement(kind, key, partyId, OnboardingRequirementStatus.CURRENTLY_DUE,
            OnboardingAwaitingParty.CLIENT, code, remediation(code), null));
    }

    /** 退回期限与审核中状态。 */
    List<OnboardingRequirement> finish() {
        ClientApplicationStatus status = input.getApplicationStatus();
        boolean returned = status == ClientApplicationStatus.RETURNED && input.getReviewDueTime() != null;
        boolean pastDue = returned && input.getNow().isAfter(input.getReviewDueTime());
        boolean underReview = status == ClientApplicationStatus.SUBMITTED || status == ClientApplicationStatus.UNDER_REVIEW;
        for (OnboardingRequirement item : items) {
            if (item.blocksSubmission() && returned) {
                item.setDueTime(input.getReviewDueTime());
                item.setStatus(pastDue ? OnboardingRequirementStatus.PAST_DUE : OnboardingRequirementStatus.CURRENTLY_DUE);
            } else if (underReview && item.getStatus() == OnboardingRequirementStatus.SATISFIED
                && item.getKind() == OnboardingRequirementKind.DOCUMENT) {
                item.setStatus(OnboardingRequirementStatus.PENDING_VERIFICATION);
                item.setAwaitingActionFrom(OnboardingAwaitingParty.OPERATIONS);
            }
        }
        return List.copyOf(items);
    }

    /** 错误码到补救动作。 */
    static OnboardingRemediationCode remediation(OnboardingRequirementCode code) {
        return switch (code) {
            case IDENTIFIER_CONFLICT -> OnboardingRemediationCode.CONTACT_SUPPORT;
            case UBO_MISSING, SIGNATORY_MISSING, DIRECTOR_MISSING, MANAGER_MISSING, CONTROL_PERSON_MISSING,
                 RELATED_PERSON_INCOMPLETE -> OnboardingRemediationCode.ADD_PERSON;
            case UBO_OWNERSHIP_EXCEEDS_100 -> OnboardingRemediationCode.ADD_RELATION;
            case DOCUMENT_MISSING, DOCUMENT_EXPIRED, DOCUMENT_TOO_OLD, DOCUMENT_UNREADABLE, DOCUMENT_TYPE_INVALID,
                 DOCUMENT_SUBJECT_REMOVED -> OnboardingRemediationCode.UPLOAD_DOCUMENT;
            case DECLARATION_MISSING -> OnboardingRemediationCode.SIGN_DECLARATION;
            default -> OnboardingRemediationCode.PROVIDE_FIELD;
        };
    }

    /**
     * 字段结论
     *
     * @param error 错误码；满足为 null
     * @param level 缺失时的要求级别；只对缺失有意义
     * @param kind 清单项类别
     */
    record FieldResult(OnboardingRequirementCode error, RequirementLevel level, OnboardingRequirementKind kind) {
        /** 满足。 */
        static FieldResult satisfied(OnboardingRequirementKind kind) {
            return new FieldResult(null, null, kind);
        }

        /** 缺失：可选字段不形成清单项。 */
        static FieldResult missing(RequirementLevel level, OnboardingRequirementKind kind) {
            return level == RequirementLevel.OPTIONAL ? null : new FieldResult(FIELD_MISSING, level, kind);
        }

        /** 有值但不合规，与要求级别无关。 */
        static FieldResult error(OnboardingRequirementCode code, OnboardingRequirementKind kind) {
            return new FieldResult(code, RequirementLevel.REQUIRED, kind);
        }

        /** 指定要求级别。 */
        FieldResult withLevel(RequirementLevel value) {
            return new FieldResult(error, value, kind);
        }

        /** 是否满足。 */
        boolean isSatisfied() {
            return error == null;
        }
    }
}
