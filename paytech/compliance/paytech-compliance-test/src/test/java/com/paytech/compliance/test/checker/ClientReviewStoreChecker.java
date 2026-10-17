package com.paytech.compliance.test.checker;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.compliance.api.request.ClientReviewClassificationsRequestBody;
import com.paytech.compliance.api.request.ClientReviewDocumentBody;
import com.paytech.compliance.api.request.ClientReviewRegistrationRequestBody;
import com.paytech.compliance.api.request.ClientReviewSubjectBody;
import com.paytech.compliance.api.enums.MaterialQualificationType;
import com.paytech.compliance.domain.ClientReviewAction;
import com.paytech.compliance.service.processor.RegisterClientReviewProcessor;
import com.paytech.compliance.test.builder.ClientReviewCaseBuilder;
import com.paytech.compliance.test.builder.MaterialProgramBuilder.Requirement;
import com.paytech.compliance.test.mapper.ClientReviewTestMapper;
import com.paytech.compliance.test.mapper.ClientReviewTestMapper.CaseRow;
import com.paytech.compliance.test.mapper.ClientReviewTestMapper.ClassificationRow;
import com.paytech.compliance.test.mapper.ClientReviewTestMapper.DecisionRow;
import com.paytech.compliance.test.mapper.ClientReviewTestMapper.DependencyRow;
import com.paytech.compliance.test.mapper.ClientReviewTestMapper.EvidenceRow;
import com.paytech.compliance.test.mapper.ClientReviewTestMapper.RequirementRow;
import com.paytech.compliance.test.mapper.ClientReviewTestMapper.StateChangeRow;
import com.paytech.compliance.test.mapper.ClientReviewTestMapper.SubjectRow;
import com.paytech.compliance.test.mocker.ClientReviewMocker.MockedCase;
import com.paytech.compliance.test.mocker.ClientReviewMocker.MockedClassification;
import com.paytech.compliance.test.mocker.ClientReviewMocker.MockedEvidence;
import com.paytech.compliance.test.mocker.ClientReviewMocker.MockedRequirement;
import com.paytech.compliance.test.mocker.ClientReviewMocker.MockedReview;
import com.paytech.compliance.test.mocker.MaterialProgramMocker.MockedProgram;

/**
 * Client 入驻审核落库事实的只读 Checker
 * <li>assertRegistered：I01 登记后的主/子任务、主体、材料要求、证据、依赖与初始状态变更，期望值来自请求与程序定义。</li>
 * <li>expect(review) 从 Mocker 插入的事实建立期望，用例只声明本次命令应改变的字段，assertStore 再逐表核对全部行，
 * 未声明的行必须保持原样，同时检查不应出现的分类、决定、状态变更与补偿任务。</li>
 *
 * @author Kenny
 */
@TestComponent
public class ClientReviewStoreChecker {
    /** 运营审核命令的触发类型。 */
    public static final String OPERATIONS_REVIEW = "OPERATIONS_REVIEW";

    private final ClientReviewTestMapper mapper;

    public ClientReviewStoreChecker(ClientReviewTestMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * I01 登记结果完整落库
     *
     * @param request 登记请求
     * @param caseId 响应返回的主任务 ID
     * @param programs 三类资格的通用程序
     * @param dependencyTypes 每名关联人员 Party ID 期望的依赖类型集合
     */
    public void assertRegistered(ClientReviewRegistrationRequestBody request, long caseId,
        Map<MaterialQualificationType, MockedProgram> programs, Map<String, List<String>> dependencyTypes) {
        long applicationId = Long.parseLong(request.getApplicationId());
        List<CaseRow> cases = mapper.casesOfApplication(applicationId);
        assertEquals(request.getSubjects().size(), cases.size(), "登记的任务数");
        List<Long> caseIds = cases.stream().map(CaseRow::caseId).toList();
        Map<Long, SubjectRow> subjects = mapper.subjects(caseIds).stream()
            .collect(Collectors.toMap(SubjectRow::caseId, Function.identity(), (a, b) -> {
                throw new AssertionError("任务存在多个主体：" + a.caseId());
            }));
        List<RequirementRow> requirements = mapper.requirements(caseIds);
        List<EvidenceRow> evidence = mapper.evidence(caseIds);
        Instant submittedAt = Instant.parse(request.getSubmittedAt());
        for (int index = 0; index < request.getSubjects().size(); index++) {
            ClientReviewSubjectBody subject = request.getSubjects().get(index);
            boolean main = index == 0;
            long roleId = Long.parseLong(main ? request.getClientId() : subject.getPartyId());
            String roleType = main ? "CLIENT" : "RELATED_PERSON";
            CaseRow row = cases.stream().filter(item -> item.roleType().equals(roleType) && item.roleId() == roleId)
                .findFirst().orElseThrow(() -> new AssertionError("缺少任务：" + roleType + ":" + roleId));
            if (main) {
                assertEquals(caseId, row.caseId(), "响应的 caseId 应为主任务");
            }
            MaterialQualificationType type = MaterialQualificationType.valueOf(subject.getQualificationType());
            MockedProgram program = programs.get(type);
            assertAll("任务 " + roleType + ":" + roleId, () -> assertEquals("DD" + row.caseId(), row.caseNo()),
                () -> assertEquals(type.getCode(), row.qualificationType()),
                () -> assertEquals(program.programVersionId(), row.programVersionId()),
                () -> assertEquals("APPLICATION", row.caseOrigin()),
                () -> assertEquals(RegisterClientReviewProcessor.NAMESPACE, row.sourceNamespace()),
                () -> assertEquals(applicationId, row.applicationId()),
                () -> assertEquals(request.getSourceSubmissionId(), row.submissionId()),
                () -> assertEquals(ClientReviewCaseBuilder.SUBMITTED, row.status()),
                () -> assertEquals(1, row.activeSlot()), () -> assertEquals(submittedAt, row.submittedTime()),
                () -> assertNull(row.decisionTime()), () -> assertEquals(1, row.version()),
                () -> assertNull(row.publicNote()), () -> assertNull(row.resolutionNote()),
                () -> assertNull(row.returnSection()));
            SubjectRow subjectRow = subjects.get(row.caseId());
            assertNotNull(subjectRow, "任务缺少主体");
            assertAll("主体 " + subject.getPartyId(),
                () -> assertEquals(Long.parseLong(subject.getPartyId()), subjectRow.partyId()),
                () -> assertEquals("PRIMARY", subjectRow.subjectRole()), () -> assertEquals(1, subjectRow.primarySlot()),
                () -> assertEquals(Long.parseLong(subject.getProfileId()), subjectRow.sourceProfileId()),
                () -> assertNull(subjectRow.sourceRelationId()), () -> assertNull(subjectRow.ownershipPercentage()),
                () -> assertEquals(1, subjectRow.requiredFlag()));
            assertRegisteredMaterials(request, subject, row, subjectRow, program, requirements, evidence);
        }
        assertEquals(request.getDocuments().size(), evidence.size(), "证据总数");
        assertRegisteredDependencies(caseId, cases, dependencyTypes);
        assertRegisteredStateChanges(request, caseIds);
        assertAll("登记不产生分类、决定或结果发布", () -> assertTrue(mapper.classifications(caseId).isEmpty()),
            () -> assertTrue(mapper.decisions(caseIds).isEmpty()), () -> assertTrue(mapper.resultEvents(caseId).isEmpty()));
    }

    /** 每个程序要求实例化一次；有材料的要求 RECEIVED，其余 PENDING；每份材料一条 RECEIVED 证据。 */
    void assertRegisteredMaterials(ClientReviewRegistrationRequestBody request, ClientReviewSubjectBody subject,
        CaseRow row, SubjectRow subjectRow, MockedProgram program, List<RequirementRow> requirements,
        List<EvidenceRow> evidence) {
        Map<String, Long> programRequirementIds = mapper.programRequirementIds(program.programVersionId());
        List<RequirementRow> own = requirements.stream().filter(item -> item.caseId() == row.caseId()).toList();
        List<String> expectedCodes = program.requirements().stream().map(Requirement::requirementCode).sorted().toList();
        assertEquals(expectedCodes, own.stream().map(RequirementRow::requirementCode).toList(), "材料要求代码");
        List<ClientReviewDocumentBody> documents = request.getDocuments().stream()
            .filter(document -> document.getSubjectPartyId().equals(subject.getPartyId())).toList();
        for (RequirementRow requirement : own) {
            boolean received = documents.stream().anyMatch(doc -> doc.getRequirementCode().equals(requirement.requirementCode()));
            assertAll("材料要求 " + requirement.requirementCode(),
                () -> assertEquals(programRequirementIds.get(requirement.requirementCode()),
                    requirement.programRequirementId()),
                () -> assertEquals(received ? ClientReviewCaseBuilder.RECEIVED : ClientReviewCaseBuilder.PENDING,
                    requirement.status()),
                () -> assertEquals(1, requirement.version()));
        }
        for (ClientReviewDocumentBody document : documents) {
            RequirementRow requirement = own.stream()
                .filter(item -> item.requirementCode().equals(document.getRequirementCode())).findFirst().orElseThrow();
            EvidenceRow row2 = evidence.stream().filter(item -> item.fileId() == Long.parseLong(document.getFileId()))
                .findFirst().orElseThrow(() -> new AssertionError("缺少证据：" + document.getFileId()));
            assertAll("证据 " + document.getFileId(), () -> assertEquals(requirement.requirementId(), row2.requirementId()),
                () -> assertEquals(subjectRow.caseSubjectId(), row2.caseSubjectId()),
                () -> assertEquals(document.getContentHash(), row2.contentHash()),
                () -> assertEquals(ClientReviewCaseBuilder.RECEIVED, row2.status()), () -> assertEquals(1, row2.version()));
        }
    }

    /** 主任务对每个子任务按依赖类型去重登记。 */
    void assertRegisteredDependencies(long caseId, List<CaseRow> cases, Map<String, List<String>> dependencyTypes) {
        List<DependencyRow> dependencies = mapper.dependencies(caseId);
        Map<String, List<String>> actual = new LinkedHashMap<>();
        for (DependencyRow dependency : dependencies) {
            CaseRow child = cases.stream().filter(item -> item.caseId() == dependency.childCaseId()).findFirst()
                .orElseThrow(() -> new AssertionError("依赖指向本次提交之外的任务"));
            actual.computeIfAbsent(Long.toString(child.roleId()), key -> new ArrayList<>()).add(dependency.dependencyType());
            assertAll("依赖 " + dependency.dependencyType(), () -> assertEquals(1, dependency.requiredFlag()),
                () -> assertNull(dependency.acceptedDecisionId()), () -> assertEquals(1, dependency.version()));
        }
        actual.replaceAll((party, types) -> types.stream().sorted().toList());
        Map<String, List<String>> expected = new LinkedHashMap<>();
        dependencyTypes.forEach((party, types) -> expected.put(party, types.stream().sorted().toList()));
        assertEquals(expected, actual, "依赖类型");
    }

    /** 每个任务一条初始状态变更：无 → SUBMITTED，由 CCT 提交触发。 */
    void assertRegisteredStateChanges(ClientReviewRegistrationRequestBody request, List<Long> caseIds) {
        List<StateChangeRow> changes = mapper.stateChanges(caseIds);
        assertEquals(caseIds.size(), changes.size(), "初始状态变更条数");
        for (StateChangeRow change : changes) {
            assertAll("状态变更 " + change.caseId(), () -> assertNull(change.fromStatus()),
                () -> assertEquals(ClientReviewCaseBuilder.SUBMITTED, change.toStatus()),
                () -> assertEquals("REGISTER", change.actionType()), () -> assertNull(change.reasonCode()),
                () -> assertEquals("CCT_CLIENT_SUBMISSION", change.triggerType()),
                () -> assertEquals(request.getSourceSubmissionId(), change.triggerId()), () -> assertNull(change.actorId()),
                () -> assertEquals(1, change.aggregateVersion()), () -> assertNotNull(change.occurredTime()));
        }
    }

    /** 该申请没有任何任务落库。 */
    public void assertNotRegistered(long applicationId) {
        assertTrue(mapper.casesOfApplication(applicationId).isEmpty(), "不应登记任何任务：" + applicationId);
    }

    /** 该申请下的任务数。 */
    public void assertCaseCount(long applicationId, int expected) {
        assertEquals(expected, mapper.casesOfApplication(applicationId).size(), "任务数：" + applicationId);
    }

    /** 以 Mocker 插入的事实为初始期望。 */
    public Expectation expect(MockedReview review) {
        return new Expectation(review);
    }

    /** 命令被拒绝或为重放：全部行保持 Mocker 插入时的原样。 */
    public void assertUnchanged(MockedReview review) {
        assertStore(expect(review));
    }

    /** 逐表核对：任务、要求、证据、分类、决定、状态变更与补偿任务。 */
    public void assertStore(Expectation expected) {
        MockedReview review = expected.review;
        List<Long> caseIds = review.caseIds();
        Map<Long, CaseRow> cases = caseIds.stream().map(mapper::findCase).filter(Objects::nonNull)
            .collect(Collectors.toMap(CaseRow::caseId, Function.identity()));
        assertEquals(caseIds.size(), cases.size(), "任务行数");
        expected.cases.forEach((caseId, state) -> {
            CaseRow row = cases.get(caseId);
            assertAll("任务 " + caseId, () -> assertEquals(state.status, row.status()),
                () -> assertEquals(state.active ? 1 : null, row.activeSlot()),
                () -> assertEquals(state.version, row.version()), () -> assertEquals(state.publicNote, row.publicNote()),
                () -> assertEquals(state.resolutionNote, row.resolutionNote()),
                () -> assertEquals(state.returnSection, row.returnSection()),
                () -> assertEquals(state.decisionTime == null, row.decisionTime() == null, "decision_time 是否为空"),
                () -> assertEquals(review.spec().submittedTime(), row.submittedTime()));
            if (state.decisionTime != null && state.decisionTime != Expectation.NOW) {
                assertEquals(state.decisionTime, row.decisionTime(), "decision_time");
            }
        });
        assertRows("材料要求", expected.requirements, mapper.requirements(caseIds), RequirementRow::requirementId,
            row -> new ItemState(row.status(), row.version()));
        assertRows("证据", expected.evidence, mapper.evidence(caseIds), EvidenceRow::evidenceId,
            row -> new ItemState(row.status(), row.version()));
        assertClassifications(expected);
        assertDecisionsAndChanges(expected, cases);
        if (expected.decision == null) {
            assertNoResultEvent(review.caseId());
        }
    }

    /** 同一张表的全部行与期望一一对应。 */
    <R> void assertRows(String name, Map<Long, ItemState> expected, List<R> rows, Function<R, Long> id,
        Function<R, ItemState> state) {
        Map<Long, ItemState> actual = rows.stream().collect(Collectors.toMap(id, state));
        assertEquals(expected, actual, name);
    }

    /** 已接收分类与期望一致。 */
    void assertClassifications(Expectation expected) {
        List<ClassificationRow> rows = mapper.classifications(expected.review.caseId());
        List<String> actual = rows.stream()
            .map(row -> row.industryCode() + ":" + row.mccCode() + ":" + row.primarySlot() + ":" + row.confirmedBy() + ":"
                + row.confirmedTime())
            .sorted().toList();
        List<String> wanted = expected.classifications.stream().sorted().toList();
        assertEquals(wanted, actual, "已接收分类");
        if (!expected.newClassificationIds) {
            assertEquals(expected.review.classifications().stream().map(MockedClassification::classificationId).sorted()
                .toList(), rows.stream().map(ClassificationRow::classificationId).sorted().toList(), "分类行 ID");
        }
    }

    /** 决定与状态变更：初始登记变更保留，新增的只能是期望的结论变更。 */
    void assertDecisionsAndChanges(Expectation expected, Map<Long, CaseRow> cases) {
        MockedReview review = expected.review;
        List<StateChangeRow> changes = mapper.stateChanges(review.caseIds());
        List<StateChangeRow> registered = changes.stream().filter(row -> "REGISTER".equals(row.actionType())).toList();
        assertEquals(review.cases().size(), registered.size(), "初始登记状态变更");
        List<StateChangeRow> added = changes.stream().filter(row -> !"REGISTER".equals(row.actionType()))
            .sorted(Comparator.comparing(StateChangeRow::caseId)).toList();
        List<DecisionRow> decisions = mapper.decisions(review.caseIds());
        if (expected.decision == null) {
            assertAll("不应形成结论", () -> assertTrue(added.isEmpty(), "不应新增状态变更：" + added),
                () -> assertTrue(decisions.isEmpty(), "不应形成资格决定：" + decisions));
            return;
        }
        Decision decision = expected.decision;
        CaseRow main = cases.get(review.caseId());
        Instant now = main.decisionTime();
        assertNotNull(now, "主任务结束时间");
        assertTrue(!now.isBefore(decision.startedAt.truncatedTo(ChronoUnit.MICROS)) && !now.isAfter(Instant.now()),
            "结束时间应为本次命令执行时间：" + now);
        List<MockedCase> closed = new ArrayList<>();
        closed.add(review.main());
        review.cases().stream().skip(1).filter(MockedCase::active).forEach(closed::add);
        assertEquals(closed.stream().map(MockedCase::caseId).sorted().toList(),
            added.stream().map(StateChangeRow::caseId).sorted().toList(), "结论状态变更覆盖主任务与进行中的子任务");
        for (MockedCase item : closed) {
            boolean isMain = item.caseId() == review.caseId();
            StateChangeRow change = added.stream().filter(row -> row.caseId() == item.caseId()).findFirst().orElseThrow();
            String toStatus = isMain ? decision.action.getCaseStatus() : decision.action.getChildStatus();
            String actionType = !isMain && decision.action == ClientReviewAction.REJECT ? "WITHDRAW_WITH_PARENT"
                : decision.action.name();
            assertEquals(now, cases.get(item.caseId()).decisionTime(), "同一命令的结束时间一致");
            assertAll("结论状态变更 " + item.caseId(), () -> assertEquals(item.status(), change.fromStatus()),
                () -> assertEquals(toStatus, change.toStatus()), () -> assertEquals(actionType, change.actionType()),
                () -> assertEquals(decision.reasonCode, change.reasonCode()),
                () -> assertEquals(OPERATIONS_REVIEW, change.triggerType()),
                () -> assertEquals("CLIENT_REVIEW:" + review.caseId() + ":" + decision.action.name(), change.triggerId()),
                () -> assertEquals(decision.actorId, change.actorId()),
                () -> assertEquals(item.version() + 1, change.aggregateVersion()),
                () -> assertEquals(now, change.occurredTime()));
        }
        List<MockedCase> decided = decision.action == ClientReviewAction.APPROVE ? closed
            : decision.action == ClientReviewAction.REJECT ? List.of(review.main()) : List.of();
        assertEquals(decided.stream().map(MockedCase::caseId).sorted().toList(),
            decisions.stream().map(DecisionRow::caseId).sorted().toList(), "资格决定覆盖的任务");
        for (MockedCase item : decided) {
            DecisionRow row = decisions.stream().filter(d -> d.caseId() == item.caseId()).findFirst().orElseThrow();
            CaseRow caseRow = cases.get(item.caseId());
            assertAll("资格决定 " + item.caseId(), () -> assertNull(row.decisionVersion()),
                () -> assertEquals(caseRow.roleType(), row.roleType()), () -> assertEquals(caseRow.roleId(), row.roleId()),
                () -> assertEquals(item.qualificationType().getCode(), row.qualificationType()),
                () -> assertEquals("REVIEWER", row.decisionSource()),
                () -> assertEquals(decision.action.getDecisionOutcome(), row.outcome()),
                () -> assertEquals(now, row.validFrom()), () -> assertNull(row.validTo()),
                () -> assertEquals(decision.actorId, row.decidedBy()), () -> assertEquals(now, row.decidedTime()));
        }
    }

    /** 某任务当前的资格决定 ID；不存在时失败。 */
    public long decisionId(long caseId) {
        List<DecisionRow> rows = mapper.decisions(List.of(caseId));
        assertEquals(1, rows.size(), "主任务资格决定数");
        return rows.getFirst().decisionId();
    }

    /** 读取任务当前行。 */
    public CaseRow caseRow(long caseId) {
        CaseRow row = mapper.findCase(caseId);
        assertNotNull(row, "任务不存在：" + caseId);
        return row;
    }

    /**
     * 不属于给定申请的 Client 入驻主任务数，筛选条件与列表接口相同；用于在共享库中计算列表总数
     *
     * @param status 可选状态
     * @param qualificationType 可选资格类型
     * @param reviews 本例插入的任务
     * @return 其他主任务数
     */
    public long otherClientCases(String status, String qualificationType, MockedReview... reviews) {
        return mapper.countOtherClientCases(status, qualificationType,
            java.util.Arrays.stream(reviews).map(MockedReview::applicationId).toList());
    }

    /** 某主任务没有审核结果补偿任务残留。 */
    public void assertNoResultEvent(long caseId) {
        assertTrue(mapper.resultEvents(caseId).isEmpty(), "不应残留审核结果补偿任务：" + caseId);
    }

    /** 状态与版本。 */
    record ItemState(String status, long version) {}

    /** 任务的期望状态。 */
    static final class CaseState {
        String status;

        boolean active;

        long version;

        String publicNote;

        String resolutionNote;

        String returnSection;

        Instant decisionTime;
    }

    /** 期望的结论。 */
    record Decision(ClientReviewAction action, long actorId, String reasonCode, Instant startedAt) {}

    /** 从 Mocker 插入的事实出发的期望，用例只声明变化。 */
    public static final class Expectation {
        /** 表示由本次命令写入的结束时间，具体值与决定、状态变更交叉核对。 */
        static final Instant NOW = Instant.EPOCH;

        final MockedReview review;

        final Map<Long, CaseState> cases = new LinkedHashMap<>();

        final Map<Long, ItemState> requirements = new LinkedHashMap<>();

        final Map<Long, ItemState> evidence = new LinkedHashMap<>();

        final List<String> classifications = new ArrayList<>();

        boolean newClassificationIds;

        Decision decision;

        Expectation(MockedReview review) {
            this.review = review;
            ClientReviewCaseBuilder spec = review.spec();
            for (MockedCase item : review.cases()) {
                boolean main = item.caseId() == review.caseId();
                CaseState state = new CaseState();
                state.status = item.status();
                state.active = item.active();
                state.version = item.version();
                state.publicNote = main ? spec.publicNote() : null;
                state.resolutionNote = spec.resolutionNote();
                state.returnSection = main ? spec.returnSection() : null;
                state.decisionTime = item.active() ? null : main ? spec.decisionTime() : NOW;
                cases.put(item.caseId(), state);
                for (MockedRequirement requirement : item.requirements()) {
                    requirements.put(requirement.requirementId(), new ItemState(requirement.status(), 1));
                    for (MockedEvidence row : requirement.evidence()) {
                        evidence.put(row.evidenceId(), new ItemState(row.status(), 1));
                    }
                }
            }
            for (MockedClassification row : review.classifications()) {
                classifications.add(row.industryCode() + ":" + row.mccCode() + ":" + (row.primary() ? 1 : null) + ":"
                    + row.confirmedBy() + ":" + row.confirmedAt());
            }
        }

        /** 主任务版本递增 1（核验材料、接收分类）。 */
        public Expectation mainTouched() {
            cases.get(review.caseId()).version++;
            return this;
        }

        /** 某证据核验为给定结果，版本递增。 */
        public Expectation evidence(MockedEvidence row, String status) {
            evidence.put(row.evidenceId(), new ItemState(status, 2));
            return this;
        }

        /** 某要求状态变化，版本递增。 */
        public Expectation requirement(MockedRequirement row, String status) {
            requirements.put(row.requirementId(), new ItemState(status, 2));
            return this;
        }

        /** 接收的分类集合（替换期望）。 */
        public Expectation classifications(ClientReviewClassificationsRequestBody request) {
            classifications.clear();
            Instant confirmedAt = Instant.parse(request.getConfirmedAt()).truncatedTo(ChronoUnit.MICROS);
            request.getClassifications().forEach(item -> classifications.add(item.getIndustryCode() + ":"
                + item.getMccCode() + ":" + (item.getPrimary() ? 1 : null) + ":" + request.getConfirmedBy() + ":"
                + confirmedAt));
            newClassificationIds = true;
            return this;
        }

        /**
         * 本次命令以给定结论结束主任务与仍在进行的子任务
         *
         * @param action 结论
         * @param actorId 操作人
         * @param reasonCode 原因代码
         * @param reason 内部理由；退回为空
         * @param publicNote 客户可见说明
         * @param returnSection 退回分区；非退回为空
         * @param startedAt 命令发出前的时间，结束时间不得早于它
         */
        public Expectation decided(ClientReviewAction action, long actorId, String reasonCode, String reason,
            String publicNote, String returnSection, Instant startedAt) {
            decision = new Decision(action, actorId, reasonCode, startedAt);
            for (MockedCase item : review.cases()) {
                boolean main = item.caseId() == review.caseId();
                if (!main && !item.active()) {
                    continue;
                }
                CaseState state = cases.get(item.caseId());
                state.status = main ? action.getCaseStatus() : action.getChildStatus();
                state.active = false;
                state.version = item.version() + 1;
                state.publicNote = main ? publicNote : null;
                state.resolutionNote = action == ClientReviewAction.RETURN ? null : reason;
                state.returnSection = main ? returnSection : null;
                state.decisionTime = NOW;
            }
            return this;
        }
    }
}
