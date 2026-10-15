package com.paytech.client.contract.test.mocker.onboarding;

import com.paytech.client.contract.core.dal.dataobject.ClientProjectionHistory;
import com.paytech.client.contract.domain.enums.BusinessChangeOperation;
import com.paytech.client.contract.domain.enums.BusinessChangeSnapshotFormat;
import com.paytech.client.contract.domain.enums.ClientStatus;
import com.paytech.client.contract.domainservice.ClientProjectionSnapshot;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.client.contract.test.dal.onboarding.OnboardingTestMapper;
import com.paytech.foundation.testsupport.api.Clear;

/**
 * 插入完整、可提交的入驻草稿夹具；用例只覆盖与场景有关的部分。
 * <li>标识取自保留区间并逐例登记，清理只删除本例插入及系统随之生成的行。</li>
 */
@TestComponent
public class OnboardingApplicationMocker implements Clear {
    /** 一个申请夹具的全部标识。 */
    public record ApplicationFixture(long accountId, long applicationId, long clientId, long partyId, long profileId,
        boolean organization) {
    }

    /** 已登记审核的申请：当前提交、主审核任务与 Client 第一版生命周期摘要。 */
    public record ReviewFixture(ApplicationFixture app, String submissionId, long caseId, String firstPayloadHash) {
    }

    /** 一个关联人员夹具。 */
    public record PersonFixture(long partyId, long profileId, long relationId) {
    }

    private static final AtomicLong IDS = new AtomicLong(9_300_000_000_000_000L + (System.nanoTime() % 1_000_000L) * 1_000L);

    private final OnboardingTestMapper mapper;
    private final Set<Long> applications = new LinkedHashSet<>();
    private final Set<Long> parties = new LinkedHashSet<>();
    private final Set<Long> clients = new LinkedHashSet<>();

    /** 使用入驻 TestMapper。 */
    public OnboardingApplicationMocker(OnboardingTestMapper mapper) {
        this.mapper = mapper;
    }

    /** 新的保留区间标识。 */
    public long nextId() {
        return IDS.incrementAndGet();
    }

    /** 完整的个人申请。 */
    public ApplicationFixture individual(long accountId) {
        return application(accountId, false);
    }

    /** 完整的组织申请（尚无关联人员）。 */
    public ApplicationFixture organization(long accountId) {
        return application(accountId, true);
    }

    /** 给组织申请添加一名完整的关联人员及草稿关系。 */
    public PersonFixture person(ApplicationFixture app, String relationType) {
        long partyId = nextId(), profileId = nextId(), relationId = nextId();
        parties.add(partyId);
        mapper.insertSubject(partyId, profileId, app.applicationId(), false, "RESIDENTIAL", nextId(), nextId(), now());
        mapper.insertRelation(relationId, app.applicationId(), app.partyId(), partyId, relationType, now());
        return new PersonFixture(partyId, profileId, relationId);
    }

    /** 给主体关联一份材料，返回材料引用 ID。 */
    public long document(ApplicationFixture app, long subjectPartyId, String requirementCode, String documentType, long fileId,
        String contentHash) {
        long documentId = nextId();
        mapper.insertDocument(documentId, app.applicationId(), subjectPartyId, requirementCode, documentType, fileId, contentHash, now());
        return documentId;
    }

    /** 某个其他主体已占用的身份键。 */
    public long foreignIdentity(String type, String scope, String normalized) {
        long partyId = nextId();
        parties.add(partyId);
        mapper.insertSubject(partyId, nextId(), null, false, "RESIDENTIAL", nextId(), nextId(), now());
        mapper.insertIdentityKey(nextId(), partyId, type, scope, normalized, now());
        return partyId;
    }

    /**
     * 完整的个人申请并置为审核中
     * <li>同时插入 Client 第一版 PENDING 生命周期历史，与正式草稿创建写入的历史形状一致。</li>
     */
    public ReviewFixture underReview(long accountId) {
        ApplicationFixture app = individual(accountId);
        String submissionId = java.util.UUID.randomUUID().toString();
        long caseId = nextId();
        Instant now = now();
        mapper.underReview(app.applicationId(), submissionId, caseId, now);
        long historyId = nextId(), changeId = nextId();
        var history = new ClientProjectionHistory(changeId, historyId, Long.toString(app.clientId()), 1, BusinessChangeOperation.CREATE,
            BusinessChangeSnapshotFormat.ENTITY_NO, null, accountId, now, app.clientId(), app.partyId(), "TC" + app.clientId(), accountId, 1);
        String snapshot = ClientProjectionSnapshot.snapshot(history, ClientStatus.PENDING, Long.toString(nextId()), "CLIENT_CREATED");
        mapper.insertClientHistory(historyId, changeId, app.clientId(), accountId, snapshot, now);
        var stored = new ClientProjectionHistory(changeId, historyId, Long.toString(app.clientId()), 1, BusinessChangeOperation.CREATE,
            BusinessChangeSnapshotFormat.ENTITY_NO, snapshot, accountId, now, app.clientId(), app.partyId(), "TC" + app.clientId(), accountId, 1);
        return new ReviewFixture(app, submissionId, caseId, ClientProjectionSnapshot.read(stored).payloadHash());
    }

    /** 登记经 HTTP 新建的申请，随本例一起清理。 */
    public void track(long applicationId) {
        applications.add(applicationId);
    }

    /** 插入主体与申请。 */
    ApplicationFixture application(long accountId, boolean organization) {
        long applicationId = nextId(), clientId = nextId(), partyId = nextId(), profileId = nextId();
        applications.add(applicationId);
        parties.add(partyId);
        clients.add(clientId);
        mapper.insertSubject(partyId, profileId, applicationId, organization, organization ? "REGISTERED" : "RESIDENTIAL", nextId(),
            nextId(), now());
        mapper.insertApplication(applicationId, clientId, partyId, profileId, nextId(), accountId, organization, nextId(), now());
        nextId();
        return new ApplicationFixture(accountId, applicationId, clientId, partyId, profileId, organization);
    }

    /** UTC 微秒时间。 */
    static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** 上一例必须已清理。 */
    public void assertEmpty() {
        if (!parties.isEmpty() || mapper.reviewEventCount() != 0) {
            throw new IllegalStateException("入驻测试环境未清理，禁止复用");
        }
    }

    /** 删除本例全部夹具。 */
    @Override
    public void clear() {
        try {
            mapper.delete(applications, parties, clients);
        } finally {
            applications.clear();
            parties.clear();
            clients.clear();
        }
    }
}
