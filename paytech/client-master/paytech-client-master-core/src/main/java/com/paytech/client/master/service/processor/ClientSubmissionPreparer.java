package com.paytech.client.master.service.processor;

import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.paytech.client.master.core.dal.dataobject.ApplicationDeclaration;
import com.paytech.client.master.core.dal.dataobject.ClientApplicationDocument;
import com.paytech.client.master.core.dal.dataobject.ClientDraftFields;
import com.paytech.client.master.core.dal.dataobject.ClientDraftIdentity;
import com.paytech.client.master.core.dal.dataobject.ClientPartyRelation;
import com.paytech.client.master.core.dal.dataobject.IdentifierType;
import com.paytech.client.master.core.dal.dataobject.PartyAttribute;
import com.paytech.client.master.domain.enums.ClientType;
import com.paytech.client.master.domain.enums.IssuerScopeMode;
import com.paytech.client.master.domain.enums.PartyRelationStatus;
import com.paytech.client.master.domain.enums.RuleRole;
import com.paytech.client.master.domain.enums.SchemaSubject;
import com.paytech.client.master.domain.onboarding.ClientAgreementPartyUsage;
import com.paytech.client.master.domain.onboarding.ClientDraftPolicy;
import com.paytech.client.master.domain.onboarding.ClientDraftValues;
import com.paytech.client.master.domain.onboarding.ClientIdentifierNormalizer;
import com.paytech.client.master.domain.onboarding.ClientPersonView;
import com.paytech.client.master.domain.onboarding.ClientSubmissionPreparation;
import com.paytech.client.master.domain.onboarding.ClientSubmissionSubject;
import com.paytech.client.master.domain.onboarding.pack.OnboardingPack;
import com.paytech.client.master.domain.onboarding.requirement.DocumentFact;
import com.paytech.client.master.domain.onboarding.requirement.IdentifierFact;
import com.paytech.client.master.domain.onboarding.requirement.MaterialRequirement;
import com.paytech.client.master.domain.onboarding.requirement.OnboardingRequirementEvaluator;
import com.paytech.client.master.domain.onboarding.requirement.RelationFact;
import com.paytech.client.master.domain.onboarding.requirement.RequirementInput;
import com.paytech.client.master.domain.onboarding.requirement.RequirementSubject;
import com.paytech.client.master.domainservice.repository.ApplicationDeclarationRepository;
import com.paytech.client.master.domainservice.repository.ClientDocumentRepository;
import com.paytech.client.master.domainservice.repository.ClientDraftRepository;
import com.paytech.client.master.domainservice.repository.ClientPeopleRepository;
import com.paytech.client.master.domainservice.repository.PartyAttributeRepository;
import com.paytech.client.master.domainservice.repository.PartyIdentityKeyRepository;
import com.paytech.client.master.domainservice.repository.PartyProfileIdentifierRepository;
import com.paytech.client.master.domainservice.service.OnboardingPackService;
import com.paytech.client.master.integration.ClientAgreementPartyClient;
import com.paytech.client.master.integration.ClientMaterialAuthorityClient;
import com.paytech.client.master.integration.ComplianceProgramClient;
import com.paytech.compliance.api.enums.MaterialQualificationType;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.api.files.FileAddress;

import lombok.AllArgsConstructor;

/**
 * C14 提交、提交核对与需求清单共用的准备步骤（需求 B2、B3）。
 * <li>只读：查询本模块仓储与辖区包，并在数据库事务外读取 compliance 程序材料、commercial 合同引用和 Platform 文件状态。</li>
 * <li>规则由辖区包数据与 OnboardingRequirementEvaluator 承担；本类只装载事实、计算证件唯一性作用域与冲突。</li>
 */
@AllArgsConstructor
public class ClientSubmissionPreparer {
    private final Clock clock;
    private final OnboardingPackService packs;
    private final ClientDraftRepository drafts;
    private final ClientPeopleRepository people;
    private final ClientDocumentRepository documents;
    private final PartyAttributeRepository attributes;
    private final ComplianceProgramClient programs;
    private final PartyIdentityKeyRepository identityKeys;
    private final ClientMaterialAuthorityClient authority;
    private final ClientAgreementPartyClient agreementParties;
    private final PartyProfileIdentifierRepository identifiers;
    private final ApplicationDeclarationRepository declarations;

    /**
     * 装载申请的全部提交事实并计算需求清单
     *
     * @param accountId 当前账号（Platform 文件上传人核对）
     * @param identity 已核对归属的申请
     * @return 主体、关系、材料、声明与需求清单
     */
    public ClientSubmissionPreparation prepare(long accountId, ClientDraftIdentity identity) {
        OnboardingPack pack = packs.load(identity.schemaId());
        List<ClientPartyRelation> relations = people.relations(identity).stream()
            .filter(relation -> relation.status() == PartyRelationStatus.DRAFT || relation.status() == PartyRelationStatus.ACTIVE)
            .toList();
        List<ClientSubmissionSubject> subjects = subjects(identity, relations, pack);
        List<ClientApplicationDocument> materials = documents.documents(identity.applicationId());
        List<ApplicationDeclaration> declared = declarations.findByApplication(identity.applicationId());
        List<MaterialRequirement> programMaterials = programs.materials(
            OnboardingScopes.scope(pack, identity.clientType(), identity.responsiblePartnerId()));
        Set<Long> unusable = unusableDocuments(accountId, subjects, materials);
        var input = new RequirementInput(pack, identity.organizationTypeCode(), requirementSubjects(subjects, relations, pack),
            relations.stream().map(relation -> new RelationFact(relation.relatedPartyId(), relation.relationType(),
                relation.ownershipPercentage(), relation.uboBasis())).toList(),
            materials.stream().map(document -> new DocumentFact(document.documentId(), document.subjectPartyId(),
                document.requirementCode(), document.documentType(), document.issueDate(), document.expiryDate(),
                !unusable.contains(document.documentId()))).toList(),
            programMaterials, latestDeclarations(declared), identity.status(), identity.reviewDueTime(), now());
        return new ClientSubmissionPreparation(subjects, relations, materials, declared,
            OnboardingRequirementEvaluator.evaluate(input));
    }

    /** 主主体在前，随后是仍有 DRAFT/ACTIVE 关系的关联人员；计算作用域、已有身份键与申请内重复。 */
    List<ClientSubmissionSubject> subjects(ClientDraftIdentity identity, List<ClientPartyRelation> relations, OnboardingPack pack) {
        boolean organization = identity.clientType() == ClientType.ORGANIZATION;
        var rows = new ArrayList<ClientSubmissionSubject>();
        ClientDraftFields primaryFields = drafts.fields(identity);
        ClientSubmissionSubject primary = subject(identity.partyId(), identity.profileId(), primaryFields, organization,
            ClientSubmissionSubject.PRIMARY,
            organization ? MaterialQualificationType.ORGANIZATION_KYB : MaterialQualificationType.INDIVIDUAL_KYC, true);
        primary.setContacts(drafts.contacts(identity.profileId()));
        rows.add(primary);
        if (organization) {
            List<ClientPersonView> persons = people.people(identity);
            // 准备步骤在数据库事务外执行；合同引用由 commercial 判断，无法确认时人员按已引用处理。
            ClientAgreementPartyUsage usage = agreementParties.usage(persons.stream().map(ClientPersonView::identity).toList());
            for (ClientPersonView person : persons) {
                long partyId = person.identity().partyId();
                if (relations.stream().anyMatch(relation -> relation.relatedPartyId() == partyId)) {
                    rows.add(subject(partyId, person.identity().profileId(), person.fields(), false,
                        ClientSubmissionSubject.RELATED_PERSON, MaterialQualificationType.RELATED_PERSON_KYC,
                        ClientDraftPolicy.isPersonEditable(identity, person.identity(), usage)));
                }
            }
        }
        attach(rows, pack, primaryFields);
        return rows;
    }

    /** 构造单个主体的资料与证件行。 */
    ClientSubmissionSubject subject(long partyId, long profileId, ClientDraftFields fields, boolean organization, String role,
        MaterialQualificationType qualification, boolean bindable) {
        var subject = new ClientSubmissionSubject();
        subject.setPartyId(partyId);
        subject.setProfileId(profileId);
        subject.setFields(fields);
        subject.setOrganization(organization);
        subject.setSubjectRole(role);
        subject.setQualificationType(qualification);
        subject.setBindable(bindable);
        subject.setContacts(List.of());
        subject.setIdentifier(identifiers.findByProfile(profileId));
        return subject;
    }

    /** 扩展属性、唯一性作用域、已有身份键与申请内重复。 */
    void attach(List<ClientSubmissionSubject> subjects, OnboardingPack pack, ClientDraftFields primaryFields) {
        Map<Long, List<PartyAttribute>> byProfile = new HashMap<>();
        attributes.findByProfiles(subjects.stream().map(ClientSubmissionSubject::getProfileId).toList())
            .forEach(row -> byProfile.computeIfAbsent(row.getProfileId(), ignored -> new ArrayList<>()).add(row));
        var seen = new HashMap<String, Long>();
        for (ClientSubmissionSubject subject : subjects) {
            subject.setAttributes(byProfile.getOrDefault(subject.getProfileId(), List.of()));
            ClientDraftFields fields = subject.getFields();
            subject.setIssuerScopeKey(scopeKey(pack.identifierType(fields.identifierIdentifierType()), fields, primaryFields));
            if (subject.getIssuerScopeKey() == null || subject.identifierKeyValue() == null) {
                continue;
            }
            String key = fields.identifierIdentifierType() + ":" + subject.getIssuerScopeKey() + ":" + subject.identifierKeyValue();
            Long owner = seen.putIfAbsent(key, subject.getPartyId());
            subject.setDuplicateInApplication(owner != null && owner != subject.getPartyId());
            subject.setExistingKey(identityKeys.find(fields.identifierIdentifierType(), subject.getIssuerScopeKey(),
                subject.identifierKeyValue()));
        }
    }

    /** 唯一性作用域：NONE 为 GLOBAL；COUNTRY 为签发国；AUTHORITY 为申请主主体的登记机关。依赖字段缺失为空。 */
    static String scopeKey(IdentifierType type, ClientDraftFields fields, ClientDraftFields primaryFields) {
        if (type == null) {
            return null;
        }
        if (type.getIssuerScopeMode() == IssuerScopeMode.COUNTRY) {
            return fields.identifierIssuingCountry();
        }
        if (type.getIssuerScopeMode() == IssuerScopeMode.AUTHORITY) {
            return primaryFields.organizationRegistrationAuthorityCode();
        }
        return ClientIdentifierNormalizer.GLOBAL_SCOPE;
    }

    /** 需求计算的主体事实。 */
    List<RequirementSubject> requirementSubjects(List<ClientSubmissionSubject> subjects, List<ClientPartyRelation> relations,
        OnboardingPack pack) {
        var result = new ArrayList<RequirementSubject>();
        for (ClientSubmissionSubject subject : subjects) {
            boolean primary = ClientSubmissionSubject.PRIMARY.equals(subject.getSubjectRole());
            ClientDraftFields fields = subject.getFields();
            boolean unverified = !subject.isBindable() && subject.getIdentifier() != null
                && subject.getIdentifier().getIdentityKeyId() == null;
            var identifier = new IdentifierFact(fields.identifierIdentifierType(), fields.identifierPresent(),
                fields.identifierCheckResult(), fields.identifierIssuingCountry(), fields.identifierExpiryDate(),
                fields.identifierPresent() && subject.getIssuerScopeKey() == null, subject.identityConflict() || unverified);
            result.add(new RequirementSubject(subject.getPartyId(), primary ? SchemaSubject.PRIMARY : SchemaSubject.RELATED_PERSON,
                subject.isOrganization() ? ClientType.ORGANIZATION : ClientType.INDIVIDUAL, values(subject), identifier,
                primary ? Set.of() : roles(subject.getPartyId(), relations, pack)));
        }
        return result;
    }

    /** 草稿分区字段值扁平化为 分区.字段；联系方式只表示是否已填。 */
    static Map<String, String> values(ClientSubmissionSubject subject) {
        Map<String, String> values = new LinkedHashMap<>();
        ClientDraftValues.values(subject.getFields()).forEach((group, fields) -> fields.forEach((field, value) -> {
            if (value != null) {
                values.put(group + "." + field, value);
            }
        }));
        subject.getContacts().forEach(contact -> values.put("contact." + contact.contactType().toLowerCase(java.util.Locale.ROOT),
            contact.maskedValue()));
        subject.getAttributes().forEach(row -> values.put("attributes." + row.getAttributeCode(), ClientDraftValues.attributeText(row)));
        return values;
    }

    /** 关联人员在关系中的规则角色。 */
    static Set<RuleRole> roles(long partyId, List<ClientPartyRelation> relations, OnboardingPack pack) {
        Set<RuleRole> roles = EnumSet.noneOf(RuleRole.class);
        relations.stream().filter(relation -> relation.relatedPartyId() == partyId)
            .map(relation -> pack.relationType(relation.relationType())).filter(java.util.Objects::nonNull)
            .forEach(type -> roles.add(type.getRuleRole()));
        return roles;
    }

    /** 同一类型的最新声明取值。 */
    static Map<String, String> latestDeclarations(List<ApplicationDeclaration> declared) {
        Map<String, String> result = new HashMap<>();
        declared.forEach(row -> result.put(row.getDeclarationType(), row.getDeclaredValue()));
        return result;
    }

    /** 核验范围内材料的文件当前可用、仍由当前账号上传且字节未变；Platform 不可用时整体 503。 */
    Set<Long> unusableDocuments(long accountId, List<ClientSubmissionSubject> subjects, List<ClientApplicationDocument> materials) {
        var inScope = new HashSet<Long>();
        subjects.forEach(subject -> inScope.add(subject.getPartyId()));
        var unusable = new HashSet<Long>();
        for (ClientApplicationDocument document : materials) {
            if (!inScope.contains(document.subjectPartyId())) {
                continue;
            }
            try {
                FileAddress address = FileAddress.parse(document.fileAddress());
                if (!document.contentHash().equals(authority.fileHash(accountId, address))) {
                    unusable.add(document.documentId());
                }
            } catch (FailException rejected) {
                if (!MATERIAL_FILE_UNAVAILABLE.getCode().equals(rejected.getCode())
                    && !DOCUMENT_NOT_FOUND.getCode().equals(rejected.getCode())) {
                    throw rejected;
                }
                unusable.add(document.documentId());
            }
        }
        return unusable;
    }

    /** 当前 UTC 微秒。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
