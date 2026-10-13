package com.paytech.risk.test.mocker.compliance;

import static com.paytech.risk.api.enums.MaterialQualificationType.INDIVIDUAL_KYC;
import static com.paytech.risk.api.enums.MaterialQualificationType.ORGANIZATION_KYB;
import static com.paytech.risk.api.enums.MaterialQualificationType.RELATED_PERSON_KYC;
import static com.paytech.risk.test.builder.compliance.ClientReviewRegistrationRequestBuilder.COMPANY_REGISTRY_EXTRACT;
import static com.paytech.risk.test.builder.compliance.ClientReviewRegistrationRequestBuilder.CONTENT_HASH;
import static com.paytech.risk.test.builder.compliance.ClientReviewRegistrationRequestBuilder.PROOF_OF_ADDRESS;
import static com.paytech.risk.test.builder.compliance.ClientReviewRegistrationRequestBuilder.REGISTRATION_CERTIFICATE;
import static com.paytech.risk.test.builder.compliance.ClientReviewRegistrationRequestBuilder.UTILITY_BILL;
import static com.paytech.risk.test.builder.compliance.ClientReviewRegistrationRequestBuilder.randomId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.risk.api.enums.MaterialQualificationType;
import com.paytech.risk.test.builder.compliance.ClientReviewCaseBuilder;
import com.paytech.risk.test.builder.compliance.ClientReviewCaseBuilder.CaseSpec;
import com.paytech.risk.test.builder.compliance.ClientReviewCaseBuilder.Classification;
import com.paytech.risk.test.builder.compliance.ClientReviewCaseBuilder.RequirementSpec;
import com.paytech.risk.test.builder.compliance.MaterialProgramBuilder;
import com.paytech.risk.test.builder.compliance.MaterialProgramBuilder.Requirement;
import com.paytech.risk.test.mapper.compliance.ClientReviewTestMapper;
import com.paytech.risk.test.mapper.compliance.ClientReviewTestMapper.CaseRow;
import com.paytech.risk.test.mapper.compliance.ClientReviewTestMapper.ClassificationRow;
import com.paytech.risk.test.mapper.compliance.ClientReviewTestMapper.DependencyRow;
import com.paytech.risk.test.mapper.compliance.ClientReviewTestMapper.EvidenceRow;
import com.paytech.risk.test.mapper.compliance.ClientReviewTestMapper.RequirementRow;
import com.paytech.risk.test.mapper.compliance.ClientReviewTestMapper.StateChangeRow;
import com.paytech.risk.test.mapper.compliance.ClientReviewTestMapper.SubjectRow;
import com.paytech.risk.test.mocker.compliance.MaterialProgramMocker.MockedProgram;

/**
 * Client 入驻审核的本地数据 Mocker
 * <li>mockPrograms() 经 MaterialProgramMocker 为三类资格各插入一个已批准通用程序并映射；组织程序要求注册证明（必需）与地址证明（非必需），
 * 个人与关联人员程序为模板的身份材料（必需）与地址证明（非必需）。</li>
 * <li>mock() 按 Builder 直接插入主任务、子任务、主体、材料要求、证据、依赖、初始状态变更与已接收分类，不经过登记接口；
 * 同一次插入的 ID 从随机 15 位基数递增，子任务、依赖与证据的 ID 顺序即登记顺序。</li>
 * <li>登记接口按请求中的申请 ID 建任务，用例调用前以 track() 登记，清理时一并删除。</li>
 * <li>Clear 先等待本例申请的审核结果补偿任务结束（避免异步发布回写已删除的数据），再按申请 ID 删除全部审核行；等待超时则保留登记，下一次清理继续。</li>
 *
 * @author Kenny
 */
@TestComponent
public class ClientReviewMocker implements Clear {
    /** 异步发布的最长等待时间。 */
    private static final long PUBLISH_WAIT_MILLIS = 15_000;

    private final ClientReviewTestMapper mapper;

    private final MaterialProgramMocker programMocker;

    private final Map<MaterialQualificationType, MockedProgram> programs = new EnumMap<>(MaterialQualificationType.class);

    private final Set<Long> applications = new LinkedHashSet<>();

    public ClientReviewMocker(ClientReviewTestMapper mapper, MaterialProgramMocker programMocker) {
        this.mapper = mapper;
        this.programMocker = programMocker;
    }

    /** 已插入的审核任务；cases 第一项为主任务，其后为子任务。 */
    public record MockedReview(long applicationId, String submissionId, long clientId, String caseNo,
        ClientReviewCaseBuilder spec, List<MockedCase> cases, List<MockedClassification> classifications) {

        /** 主任务 ID。 */
        public long caseId() {
            return main().caseId();
        }

        public MockedCase main() {
            return cases.getFirst();
        }

        /** 第 index 个子任务。 */
        public MockedCase child(int index) {
            return cases.get(index + 1);
        }

        /** 全部任务 ID，主任务在前。 */
        public List<Long> caseIds() {
            return cases.stream().map(MockedCase::caseId).toList();
        }

        /** 第 caseIndex 个任务（0 为主任务）某要求的第一份证据。 */
        public MockedEvidence evidence(int caseIndex, String requirementCode) {
            return requirement(caseIndex, requirementCode).evidence().getFirst();
        }

        /** 第 caseIndex 个任务（0 为主任务）的某要求。 */
        public MockedRequirement requirement(int caseIndex, String requirementCode) {
            return cases.get(caseIndex).requirements().stream()
                .filter(requirement -> requirement.requirementCode().equals(requirementCode)).findFirst().orElseThrow();
        }

        /** 全部证据，按任务与要求顺序。 */
        public List<MockedEvidence> allEvidence() {
            return cases.stream().flatMap(item -> item.requirements().stream())
                .flatMap(requirement -> requirement.evidence().stream()).toList();
        }
    }

    /** 已插入的一个任务。 */
    public record MockedCase(long caseId, long caseSubjectId, long partyId, long profileId, String roleType,
        MaterialQualificationType qualificationType, long programVersionId, String status, boolean active, long version,
        List<String> dependencyTypes, List<MockedRequirement> requirements) {}

    /** 已插入的材料要求，按要求代码升序。 */
    public record MockedRequirement(long requirementId, String requirementCode, String documentType, boolean required,
        String status, List<MockedEvidence> evidence) {}

    /** 已插入的证据引用。 */
    public record MockedEvidence(long evidenceId, long requirementId, long fileId, String status) {}

    /** 已插入的分类。 */
    public record MockedClassification(long classificationId, String industryCode, String mccCode, boolean primary,
        long confirmedBy, Instant confirmedAt) {}

    /** 三类资格各一个已批准通用程序并替换映射；同一用例内重复调用复用已建程序。 */
    public Map<MaterialQualificationType, MockedProgram> mockPrograms() {
        if (programs.isEmpty()) {
            programs.put(ORGANIZATION_KYB, programMocker.mock(MaterialProgramBuilder.approved(ORGANIZATION_KYB)
                .requirements(List.of(new Requirement(REGISTRATION_CERTIFICATE, COMPANY_REGISTRY_EXTRACT, 1),
                    new Requirement(PROOF_OF_ADDRESS, UTILITY_BILL, 0)))));
            programs.put(INDIVIDUAL_KYC, programMocker.mock(MaterialProgramBuilder.approved(INDIVIDUAL_KYC)));
            programs.put(RELATED_PERSON_KYC, programMocker.mock(MaterialProgramBuilder.approved(RELATED_PERSON_KYC)));
            programMocker.mapPrograms(programs.values().toArray(MockedProgram[]::new));
        }
        return Map.copyOf(programs);
    }

    /** 登记由登记接口按此申请 ID 创建的任务，清理时删除。 */
    public void track(long applicationId) {
        applications.add(applicationId);
    }

    /** 按 Builder 插入一次提交的完整审核任务。 */
    public MockedReview mock(ClientReviewCaseBuilder builder) {
        mockPrograms();
        long applicationId = randomId();
        long clientId = randomId();
        String submissionId = UUID.randomUUID().toString();
        applications.add(applicationId);
        IdSequence ids = new IdSequence(randomId());
        List<MockedCase> cases = new ArrayList<>();
        List<CaseSpec> specs = builder.allCases();
        for (int index = 0; index < specs.size(); index++) {
            boolean main = index == 0;
            cases.add(mockCase(builder, specs.get(index), main, applicationId, submissionId,
                main ? clientId : 0, ids));
        }
        mockDependencies(cases, specs, ids);
        List<MockedClassification> classifications = mockClassifications(builder, cases.getFirst().caseId(), ids);
        return new MockedReview(applicationId, submissionId, clientId, "DD" + cases.getFirst().caseId(), builder,
            List.copyOf(cases), classifications);
    }

    /** 插入一个任务及其主体、材料要求、证据与初始状态变更。 */
    MockedCase mockCase(ClientReviewCaseBuilder builder, CaseSpec spec, boolean main, long applicationId,
        String submissionId, long clientId, IdSequence ids) {
        MockedProgram program = programs.get(spec.qualificationType());
        Map<String, Long> programRequirementIds = mapper.programRequirementIds(program.programVersionId());
        long caseId = ids.next();
        long partyId = randomId();
        long profileId = randomId();
        String roleType = main ? "CLIENT" : "RELATED_PERSON";
        long version = main ? builder.version() : spec.active() ? 1 : 2;
        mapper.insertCase(new CaseRow(caseId, "DD" + caseId, roleType, main ? clientId : partyId,
            spec.qualificationType().getCode(), program.programVersionId(), "APPLICATION", builder.sourceNamespace(),
            applicationId, submissionId, main ? builder.publicNote() : null, builder.resolutionNote(),
            main ? builder.returnSection() : null, spec.status(), spec.active() ? 1 : null, builder.submittedTime(),
            spec.active() ? null : builder.decisionTime() == null ? Instant.now() : builder.decisionTime(), version));
        long subjectId = ids.next();
        mapper.insertSubject(new SubjectRow(subjectId, caseId, partyId, "PRIMARY", 1, profileId, null, null, 1));
        List<MockedRequirement> requirements = new ArrayList<>();
        for (Requirement template : program.requirements().stream()
            .sorted((a, b) -> a.requirementCode().compareTo(b.requirementCode())).toList()) {
            RequirementSpec requirement = spec.requirements().get(template.requirementCode());
            if (requirement == null) {
                continue;
            }
            requirements.add(mockRequirement(caseId, subjectId, template,
                programRequirementIds.get(template.requirementCode()), requirement, ids));
        }
        mapper.insertStateChange(new StateChangeRow(ids.next(), caseId, null, ClientReviewCaseBuilder.SUBMITTED,
            "REGISTER", null, "CCT_CLIENT_SUBMISSION", submissionId, null, 1, builder.submittedTime()));
        return new MockedCase(caseId, subjectId, partyId, profileId, roleType, spec.qualificationType(),
            program.programVersionId(), spec.status(), spec.active(), version, spec.dependencyTypes(),
            List.copyOf(requirements));
    }

    /** 插入一个材料要求及其证据。 */
    MockedRequirement mockRequirement(long caseId, long subjectId, Requirement template, long programRequirementId,
        RequirementSpec spec, IdSequence ids) {
        long requirementId = ids.next();
        mapper.insertRequirement(new RequirementRow(requirementId, caseId, programRequirementId,
            template.requirementCode(), spec.status(), 1));
        List<MockedEvidence> evidence = new ArrayList<>();
        for (String status : spec.evidence()) {
            MockedEvidence row = new MockedEvidence(ids.next(), requirementId, randomId(), status);
            mapper.insertEvidence(new EvidenceRow(row.evidenceId(), requirementId, subjectId, row.fileId(), CONTENT_HASH,
                status, 1));
            evidence.add(row);
        }
        return new MockedRequirement(requirementId, template.requirementCode(), template.documentType(),
            template.requiredFlag() == 1, spec.status(), List.copyOf(evidence));
    }

    /** 主任务对每个子任务按依赖类型登记顺序插入依赖。 */
    void mockDependencies(List<MockedCase> cases, List<CaseSpec> specs, IdSequence ids) {
        for (int index = 1; index < cases.size(); index++) {
            for (String type : specs.get(index).dependencyTypes()) {
                mapper.insertDependency(new DependencyRow(ids.next(), cases.getFirst().caseId(), cases.get(index).caseId(),
                    type, 1, null, 1));
            }
        }
    }

    /** 插入已接收分类。 */
    List<MockedClassification> mockClassifications(ClientReviewCaseBuilder builder, long caseId, IdSequence ids) {
        List<MockedClassification> rows = new ArrayList<>();
        for (Classification item : builder.classifications()) {
            MockedClassification row = new MockedClassification(ids.next(), item.industryCode(), item.mccCode(),
                item.primary(), builder.confirmedBy(), builder.confirmedAt());
            mapper.insertClassification(new ClassificationRow(row.classificationId(), caseId, item.industryCode(),
                item.mccCode(), item.primary() ? 1 : null, builder.confirmedBy(), builder.confirmedAt()));
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    /** 先等待异步发布结束，再删除本例全部审核行；程序由 MaterialProgramMocker 自行清理。 */
    @Override
    public void clear() {
        programs.clear();
        if (applications.isEmpty()) {
            return;
        }
        awaitResultPublications();
        mapper.deleteApplications(applications);
        applications.clear();
    }

    /** 本例申请的审核结果补偿任务全部离开 INIT/PROCESSING；超时说明仍有后台写入，拒绝清理。 */
    void awaitResultPublications() {
        long deadline = System.currentTimeMillis() + PUBLISH_WAIT_MILLIS;
        while (mapper.pendingResultEvents(applications) > 0) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("审核结果补偿任务仍在执行，拒绝清理申请：" + applications);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("等待审核结果补偿任务被中断", interrupted);
            }
        }
    }

    /** 同一次插入内递增的 ID。 */
    static final class IdSequence {
        private long next;

        IdSequence(long base) {
            this.next = base;
        }

        long next() {
            return next++;
        }
    }
}
