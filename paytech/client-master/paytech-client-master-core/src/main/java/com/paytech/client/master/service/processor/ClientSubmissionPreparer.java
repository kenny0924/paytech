package com.paytech.client.master.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.paytech.client.master.core.dal.dataobject.ClientApplicationDocument;
import com.paytech.client.master.core.dal.dataobject.ClientDraftFields;
import com.paytech.client.master.core.dal.dataobject.ClientDraftIdentity;
import com.paytech.client.master.core.dal.dataobject.ClientPartyRelation;
import com.paytech.client.master.domain.enums.ClientType;
import com.paytech.client.master.domain.enums.PartyRelationStatus;
import com.paytech.client.master.domain.onboarding.ClientAgreementPartyUsage;
import com.paytech.client.master.domain.onboarding.ClientDraftPolicy;
import com.paytech.client.master.domain.onboarding.ClientIdentifierNormalizer;
import com.paytech.client.master.domain.onboarding.ClientMaterialTarget;
import com.paytech.client.master.domain.onboarding.ClientPersonView;
import com.paytech.client.master.domain.onboarding.ClientSubmissionPreparation;
import com.paytech.client.master.domain.onboarding.ClientSubmissionSubject;
import com.paytech.client.master.domainservice.repository.ClientDocumentRepository;
import com.paytech.client.master.domainservice.repository.ClientDraftRepository;
import com.paytech.client.master.domainservice.repository.ClientPeopleRepository;
import com.paytech.client.master.domainservice.repository.PartyIdentityKeyRepository;
import com.paytech.client.master.domainservice.repository.PartyProfileIdentifierRepository;
import com.paytech.client.master.domainservice.validator.ClientSubmissionValidator;
import com.paytech.client.master.integration.ClientAgreementPartyClient;
import com.paytech.client.master.integration.ClientMaterialAuthorityClient;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.risk.api.compliance.MaterialRequirementsModels.RequirementBody;
import com.paytech.risk.api.enums.MaterialQualificationType;

import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;

/**
 * C14 提交与提交核对共用的准备步骤。
 * <li>只读：查询本模块仓储，并在数据库事务外读取 Risk 材料要求和 Platform 文件状态。</li>
 * <li>检查规则由 ClientSubmissionValidator 承担；本类负责装载事实与计算证件身份。</li>
 */
public class ClientSubmissionPreparer {
    private static final String IDENTITY_CONFLICT_MESSAGE = "证件号码已被其他主体使用，请核对后联系客服";

    private final Clock clock;
    private final ClientDraftRepository drafts;
    private final ClientPeopleRepository people;
    private final ClientDocumentRepository documents;
    private final ClientSubmissionValidator validator;
    private final PartyIdentityKeyRepository identityKeys;
    private final ClientMaterialAuthorityClient authority;
    private final ClientAgreementPartyClient agreementParties;
    private final PartyProfileIdentifierRepository identifiers;

    /** 只依赖本模块仓储、通用目录校验与 Owner 只读端口（含 commercial 合同引用查询）。 */
    public ClientSubmissionPreparer(Clock clock, ClientDraftRepository drafts, ClientPeopleRepository people,
        ClientDocumentRepository documents, ClientSubmissionValidator validator, PartyIdentityKeyRepository identityKeys,
        ClientMaterialAuthorityClient authority, ClientAgreementPartyClient agreementParties,
        PartyProfileIdentifierRepository identifiers) {
        this.clock = clock;
        this.drafts = drafts;
        this.people = people;
        this.documents = documents;
        this.validator = validator;
        this.identityKeys = identityKeys;
        this.authority = authority;
        this.agreementParties = agreementParties;
        this.identifiers = identifiers;
    }

    /**
     * 装载可编辑申请的全部提交事实并检查
     *
     * @param accountId 当前账号
     * @param identity 已核对归属的可编辑申请
     * @return 主体、关系、材料及逐项问题
     */
    public ClientSubmissionPreparation prepare(long accountId, ClientDraftIdentity identity) {
        List<ClientPartyRelation> relations = people.relations(identity).stream()
            .filter(relation -> relation.status() == PartyRelationStatus.DRAFT || relation.status() == PartyRelationStatus.ACTIVE)
            .toList();
        List<ClientSubmissionSubject> subjects = subjects(identity, relations);
        List<ClientApplicationDocument> materials = documents.documents(identity.applicationId());
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var issues = new ArrayList<>(validator.validate(identity.clientType(), subjects, materials, requirements(subjects),
            unusableDocuments(accountId, identity, subjects, materials), now));
        var conflicts = identityConflicts(subjects);
        issues.addAll(conflicts);
        return new ClientSubmissionPreparation(subjects, relations, materials, List.copyOf(issues), !conflicts.isEmpty());
    }

    /** 主主体在前，随后是仍有 DRAFT/ACTIVE 关系的关联人员。 */
    List<ClientSubmissionSubject> subjects(ClientDraftIdentity identity, List<ClientPartyRelation> relations) {
        boolean organization = identity.clientType() == ClientType.ORGANIZATION;
        var rows = new ArrayList<ClientSubmissionSubject>();
        ClientSubmissionSubject primary = subject(identity.partyId(), identity.profileId(), drafts.fields(identity), organization,
            ClientSubmissionSubject.PRIMARY,
            organization ? MaterialQualificationType.ORGANIZATION_KYB : MaterialQualificationType.INDIVIDUAL_KYC, true);
        primary.setContacts(drafts.contacts(identity.profileId()));
        rows.add(primary);
        if (!organization) {
            return rows;
        }
        List<ClientPersonView> persons = people.people(identity);
        // 准备步骤在数据库事务外执行；合同引用由 commercial 判断，无法确认时人员按已引用处理。
        ClientAgreementPartyUsage usage = agreementParties.usage(persons.stream().map(ClientPersonView::identity).toList());
        for (ClientPersonView person : persons) {
            long partyId = person.identity().partyId();
            if (relations.stream().anyMatch(relation -> relation.relatedPartyId() == partyId)) {
                rows.add(subject(partyId, person.identity().profileId(), person.fields(), false, ClientSubmissionSubject.RELATED_PERSON,
                    MaterialQualificationType.RELATED_PERSON_KYC, ClientDraftPolicy.isPersonEditable(identity, person.identity(), usage)));
            }
        }
        return rows;
    }

    /** 构造单个主体并计算证件规范化值、签发作用域及已存在的身份键。 */
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
        subject.setNormalizedIdentifierValue(ClientIdentifierNormalizer.normalize(fields.identifierIdentifierValue()));
        var type = validator.identifierType(fields.identifierIdentifierType(),
            organization ? ClientType.ORGANIZATION : ClientType.INDIVIDUAL);
        if (type != null && ("NONE".equals(type.issuingCountryMode()) || fields.identifierIssuingCountry() != null)) {
            subject.setIssuerScopeKey(ClientIdentifierNormalizer.issuerScopeKey(
                "REQUIRED".equals(type.issuingCountryMode()), fields.identifierIssuingCountry()));
        }
        if (subject.getIssuerScopeKey() != null && subject.getNormalizedIdentifierValue() != null) {
            subject.setExistingKey(identityKeys.find(fields.identifierIdentifierType(), subject.getIssuerScopeKey(),
                subject.getNormalizedIdentifierValue()));
        }
        return subject;
    }

    /** 每种资格类型读取一次通用材料要求。 */
    Map<MaterialQualificationType, List<RequirementBody>> requirements(List<ClientSubmissionSubject> subjects) {
        var result = new LinkedHashMap<MaterialQualificationType, List<RequirementBody>>();
        for (ClientSubmissionSubject subject : subjects) {
            result.computeIfAbsent(subject.getQualificationType(), type -> authority.requirements(type.getCode()));
        }
        return result;
    }

    /** 核验范围内材料的文件当前可用且字节未变；Platform 不可用时整体 503。 */
    Set<Long> unusableDocuments(long accountId, ClientDraftIdentity identity, List<ClientSubmissionSubject> subjects,
        List<ClientApplicationDocument> materials) {
        Map<Long, ClientSubmissionSubject> bySubject = new HashMap<>();
        subjects.forEach(subject -> bySubject.put(subject.getPartyId(), subject));
        var unusable = new HashSet<Long>();
        for (ClientApplicationDocument document : materials) {
            ClientSubmissionSubject subject = bySubject.get(document.subjectPartyId());
            if (subject == null) {
                continue;
            }
            var target = new ClientMaterialTarget(identity.applicationId(), identity.clientId(), subject.getPartyId(),
                subject.getQualificationType());
            try {
                if (!document.contentHash().equals(authority.fileHash(accountId, target, document.fileId()))) {
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

    /** 证件号已属于其他主体，或同一申请内两个主体填了同一证件，均作为问题返回且不透露归属。 */
    List<ProblemFieldError> identityConflicts(List<ClientSubmissionSubject> subjects) {
        var conflicts = new ArrayList<ProblemFieldError>();
        var seen = new HashMap<String, Long>();
        for (ClientSubmissionSubject subject : subjects) {
            String field = (ClientSubmissionSubject.PRIMARY.equals(subject.getSubjectRole()) ? ""
                : "people[" + subject.getPartyId() + "].") + "identifier.identifierValue";
            String key = subject.getFields().identifierIdentifierType() + ":" + subject.getIssuerScopeKey() + ":"
                + subject.getNormalizedIdentifierValue();
            Long owner = subject.getNormalizedIdentifierValue() == null ? null : seen.putIfAbsent(key, subject.getPartyId());
            if (subject.identityOwnedByOther() || (owner != null && owner != subject.getPartyId())) {
                conflicts.add(new ProblemFieldError(field, IDENTITY_CONFLICT_MESSAGE));
            }
        }
        return conflicts;
    }
}
