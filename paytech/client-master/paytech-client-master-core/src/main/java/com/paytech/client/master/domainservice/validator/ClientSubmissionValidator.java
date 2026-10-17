package com.paytech.client.master.domainservice.validator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.paytech.client.master.core.configuration.ClientFormCatalogProperties;
import com.paytech.client.master.core.dal.dataobject.ClientApplicationDocument;
import com.paytech.client.master.core.dal.dataobject.ClientDraftFields;
import com.paytech.client.master.domain.enums.ClientType;
import com.paytech.client.master.domain.onboarding.ClientSubmissionSubject;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.risk.api.compliance.MaterialRequirementsModels.RequirementBody;
import com.paytech.risk.api.enums.MaterialQualificationType;

/**
 * C14 提交检查：只判断“有没有、格式对不对”，材料是否充分由审核人判断。
 * <li>无副作用，不访问数据库或外部服务；字段路径与 C05/Web 的表单键一致。</li>
 */
public final class ClientSubmissionValidator {
    private static final String REQUIRED = "提交前请填写此项";

    private final ClientFormCatalogProperties catalog;

    /** 使用启动时冻结的通用目录判断代码是否有效。 */
    public ClientSubmissionValidator(ClientFormCatalogProperties catalog) {
        this.catalog = catalog;
    }

    /**
     * 检查完整提交
     *
     * @param clientType 申请客户类型
     * @param subjects 受审主体，第一项为主主体
     * @param documents 当前材料引用
     * @param requirements 各资格类型的通用材料要求
     * @param unusableDocumentIds 文件当前不可用的材料引用
     * @param now 当前时间，用于证件有效期
     * @return 逐项问题；为空表示通过
     */
    public List<ProblemFieldError> validate(ClientType clientType, List<ClientSubmissionSubject> subjects,
        List<ClientApplicationDocument> documents, Map<MaterialQualificationType, List<RequirementBody>> requirements,
        Set<Long> unusableDocumentIds, Instant now) {
        var errors = new ArrayList<ProblemFieldError>();
        for (ClientSubmissionSubject subject : subjects) {
            String prefix = ClientSubmissionSubject.PRIMARY.equals(subject.getSubjectRole()) ? ""
                : "people[" + subject.getPartyId() + "].";
            ClientType subjectType = subject.isOrganization() ? ClientType.ORGANIZATION : ClientType.INDIVIDUAL;
            checkProfile(errors, prefix, subject.getFields(), subject.isOrganization());
            checkIdentifier(errors, prefix, subject, subjectType, now);
            checkMaterials(errors, subject, documents, requirements.getOrDefault(subject.getQualificationType(), List.of()));
        }
        ClientSubmissionSubject primary = subjects.getFirst();
        checkBusiness(errors, primary.getFields(), clientType);
        checkContacts(errors, primary);
        if (clientType == ClientType.ORGANIZATION && subjects.size() < 2) {
            errors.add(new ProblemFieldError("people", "组织申请至少需要添加一名关联人员"));
        }
        checkDocuments(errors, subjects, documents, unusableDocumentIds);
        return errors;
    }

    /** 个人或组织基本资料及地址。 */
    void checkProfile(List<ProblemFieldError> errors, String prefix, ClientDraftFields f, boolean organization) {
        if (organization) {
            require(errors, prefix + "organization.legalName", f.organizationLegalName());
            require(errors, prefix + "organization.incorporationCountry", f.organizationIncorporationCountry());
            requireCode(errors, prefix + "organization.organizationType", f.organizationOrganizationType(),
                catalog.organizationTypes().stream().map(ClientFormCatalogProperties.OrganizationType::code).toList());
            require(errors, prefix + "organization.incorporationDate", f.organizationIncorporationDate());
        } else {
            require(errors, prefix + "individual.givenName", f.individualGivenName());
            require(errors, prefix + "individual.familyName", f.individualFamilyName());
            require(errors, prefix + "individual.birthDate", f.individualBirthDate());
            require(errors, prefix + "individual.nationalityCountry", f.individualNationalityCountry());
            require(errors, prefix + "individual.residenceCountry", f.individualResidenceCountry());
        }
        require(errors, prefix + "address.addressLine1", f.addressAddressLine1());
        require(errors, prefix + "address.city", f.addressCity());
        require(errors, prefix + "address.countryCode", f.addressCountryCode());
    }

    /** 证件类型、签发国、号码与有效期；已绑定或共享人员的身份由原绑定保证。 */
    void checkIdentifier(List<ProblemFieldError> errors, String prefix, ClientSubmissionSubject subject,
        ClientType subjectType, Instant now) {
        ClientDraftFields f = subject.getFields();
        var type = identifierType(f.identifierIdentifierType(), subjectType);
        if (type == null) {
            errors.add(new ProblemFieldError(prefix + "identifier.identifierType",
                f.identifierIdentifierType() == null ? REQUIRED : "请重新选择证件类型"));
        } else if ("REQUIRED".equals(type.issuingCountryMode())) {
            require(errors, prefix + "identifier.issuingCountry", f.identifierIssuingCountry());
        }
        if (f.identifierIdentifierValue() == null) {
            errors.add(new ProblemFieldError(prefix + "identifier.identifierValue", REQUIRED));
        } else if (subject.getNormalizedIdentifierValue() == null) {
            errors.add(new ProblemFieldError(prefix + "identifier.identifierValue", "证件号码只能包含字母、数字及常见分隔符"));
        }
        if (f.identifierValidTo() != null && !f.identifierValidTo().isAfter(now)) {
            errors.add(new ProblemFieldError(prefix + "identifier.validTo", "证件已过期，请更新证件"));
        }
        if (!subject.isBindable() && subject.getIdentifier() != null && subject.getIdentifier().getIdentityKeyId() == null) {
            errors.add(new ProblemFieldError(prefix + "identifier.identifierValue", "该人员的证件尚未完成核验，请联系客服"));
        }
    }

    /** 每个基础必需要求至少关联一份材料。 */
    void checkMaterials(List<ProblemFieldError> errors, ClientSubmissionSubject subject,
        List<ClientApplicationDocument> documents, List<RequirementBody> requirements) {
        for (RequirementBody requirement : requirements) {
            if (!requirement.requiredFlag()) {
                continue;
            }
            boolean present = documents.stream().anyMatch(document -> document.subjectPartyId() == subject.getPartyId()
                && requirement.requirementCode().equals(document.requirementCode()));
            if (!present) {
                errors.add(new ProblemFieldError("materials[" + subject.getPartyId() + "]." + requirement.requirementCode(),
                    "请上传基础必需材料"));
            }
        }
    }

    /** 经营资料只属于主主体。 */
    void checkBusiness(List<ProblemFieldError> errors, ClientDraftFields f, ClientType clientType) {
        requireCode(errors, "business.businessModelCode", f.businessBusinessModelCode(), catalog.businessModels().stream()
            .filter(model -> model.clientTypes().contains(clientType)).map(ClientFormCatalogProperties.BusinessModel::code).toList());
        require(errors, "business.businessDescription", f.businessBusinessDescription());
        requirePositive(errors, "business.expectedVolume", f.businessExpectedVolume());
        requirePositive(errors, "business.expectedTicket", f.businessExpectedTicket());
        require(errors, "business.currency", f.businessCurrency());
    }

    /** 业务联系邮箱和电话。 */
    void checkContacts(List<ProblemFieldError> errors, ClientSubmissionSubject primary) {
        var types = primary.getContacts().stream().map(contact -> contact.contactType()).toList();
        if (!types.contains("EMAIL")) {
            errors.add(new ProblemFieldError("contact.email", REQUIRED));
        }
        if (!types.contains("PHONE")) {
            errors.add(new ProblemFieldError("contact.phone", REQUIRED));
        }
    }

    /** 范围外人员的材料须先移除；文件不可用须重新上传。 */
    void checkDocuments(List<ProblemFieldError> errors, List<ClientSubmissionSubject> subjects,
        List<ClientApplicationDocument> documents, Set<Long> unusableDocumentIds) {
        var inScope = subjects.stream().map(ClientSubmissionSubject::getPartyId).toList();
        for (ClientApplicationDocument document : documents) {
            String field = "documents[" + document.documentId() + "]";
            if (!inScope.contains(document.subjectPartyId())) {
                errors.add(new ProblemFieldError(field, "该材料所属人员已移除，请先移除这份材料"));
            } else if (unusableDocumentIds.contains(document.documentId())) {
                errors.add(new ProblemFieldError(field, "文件当前不可用，请重新上传"));
            }
        }
    }

    /** 当前客户类型可选的证件类型；旧代码或类型不适用返回 null。 */
    public ClientFormCatalogProperties.IdentifierType identifierType(String code, ClientType subjectType) {
        return catalog.identifierTypes().stream()
            .filter(type -> type.code().equals(code) && type.clientTypes().contains(subjectType)).findFirst().orElse(null);
    }

    /** 必填。 */
    static void require(List<ProblemFieldError> errors, String field, Object value) {
        if (value == null) {
            errors.add(new ProblemFieldError(field, REQUIRED));
        }
    }

    /** 必填且必须是当前目录中的代码。 */
    static void requireCode(List<ProblemFieldError> errors, String field, String value, List<String> codes) {
        if (value == null) {
            errors.add(new ProblemFieldError(field, REQUIRED));
        } else if (!codes.contains(value)) {
            errors.add(new ProblemFieldError(field, "请重新选择"));
        }
    }

    /** 必填且大于零。 */
    static void requirePositive(List<ProblemFieldError> errors, String field, BigDecimal value) {
        if (value == null) {
            errors.add(new ProblemFieldError(field, REQUIRED));
        } else if (value.signum() <= 0) {
            errors.add(new ProblemFieldError(field, "金额必须大于零"));
        }
    }
}
