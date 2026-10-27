package com.paytech.compliance.service.handler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.compliance.core.configuration.ScreeningProperties;
import com.paytech.compliance.core.dal.dataobject.CaseSubject;
import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.compliance.core.dal.dataobject.ProgramScreening;
import com.paytech.compliance.core.dal.dataobject.ScreeningHit;
import com.paytech.compliance.core.dal.dataobject.ScreeningHitList;
import com.paytech.compliance.core.dal.dataobject.ScreeningRun;
import com.paytech.compliance.core.enums.DueDiligenceCaseStatus;
import com.paytech.compliance.core.enums.ReviewCodes;
import com.paytech.compliance.domain.ClientReviewFamily;
import com.paytech.compliance.domain.ScreeningSearchCommand;
import com.paytech.compliance.domain.ScreeningSearchResult;
import com.paytech.compliance.domain.ScreeningSubject;
import com.paytech.compliance.domainservice.port.ComplianceIdentifierGenerator;
import com.paytech.compliance.domainservice.service.ClientReviewService;
import com.paytech.compliance.integration.ScreeningSearchGateway;
import com.paytech.compliance.integration.ScreeningSubjectDirectory;
import com.paytech.compliance.service.processor.ClientReviewFamilyLoader;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;


/**
 * 案件名单筛查（CASE_SCREENING）：立案或审核员重跑后，在事务外对主案件与子案件的每个主体、每个筛查模板执行一次 SCREENING_SEARCH
 * <li>eventKey 为主案件 ID；subEventType 为 {@value #INITIAL_RUN}（立案）或重跑请求 ID，记入执行的 trigger_ref。</li>
 * <li>每次执行先以短事务记 STARTED，再经 client-master 读取主体名称、经 channel-connector 调用渠道，结果另开短事务落执行、命中与名单类型；
 * 成功执行取代同一主体同一模板旧执行的命中。</li>
 * <li>同一 trigger_ref 已成功的模板不再执行；任一模板未成功（FAIL / 结果未知 / 依赖不可用）时整体重试。主案件已退回或结束时不执行。</li>
 * <li>不虚构成功：渠道不可用时执行保持失败或未知，批准门禁 SCREENING_INCOMPLETE 阻断。名称只在内存中使用，不落库、不进错误摘要。</li>
 */
@Component
public class CaseScreeningHandler implements CompensationEventHandler {
    /** 事件类型。 */
    public static final String EVENT_TYPE = "CASE_SCREENING";

    /** 立案触发的子类型。 */
    public static final String INITIAL_RUN = "-";

    private final Clock clock;
    private final ClientReviewService reviews;
    private final ComplianceIdentifierGenerator ids;
    private final ScreeningSearchGateway gateway;
    private final ScreeningProperties properties;
    private final ClientReviewFamilyLoader families;
    private final ScreeningSubjectDirectory directory;

    /**
     * 注入协作者；保存 Service 延迟解析，避免与补偿登记器（Service 依赖登记器、登记器收集全部 Handler）形成构造循环
     *
     * @param clock 时间源
     * @param reviews 审核本地保存（延迟代理，仅在执行时使用）
     * @param ids 标识生成
     * @param gateway 筛查渠道端口
     * @param properties 筛查配置
     * @param families 审核全貌读取
     * @param directory 主体名称端口
     */
    public CaseScreeningHandler(Clock clock, @Lazy ClientReviewService reviews, ComplianceIdentifierGenerator ids,
        ScreeningSearchGateway gateway, ScreeningProperties properties, ClientReviewFamilyLoader families,
        ScreeningSubjectDirectory directory) {
        this.clock = clock;
        this.reviews = reviews;
        this.ids = ids;
        this.gateway = gateway;
        this.properties = properties;
        this.families = families;
        this.directory = directory;
    }

    /**
     * 纯工厂：只能与立案或重跑请求在同一本地事务登记
     *
     * @param caseId 主案件 ID
     * @param triggerRef {@value #INITIAL_RUN} 或重跑请求 ID
     * @return 登记请求
     */
    public static CompensationEventRequest buildEvent(long caseId, String triggerRef) {
        if (caseId <= 0 || triggerRef == null || triggerRef.isBlank()) {
            throw new IllegalArgumentException("Invalid screening identity");
        }
        return new CompensationEventRequest(Long.toString(caseId), EVENT_TYPE, triggerRef);
    }

    /** 事件类型。 */
    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /** 每次尝试 60 秒，最多 6 次，30 秒起指数退避至 10 分钟；用尽后由审核员重跑或人工名单检查兜底。 */
    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(60), 6, Duration.ofSeconds(30), Duration.ofMinutes(10), 2, 0.2);
    }

    /** 执行本次触发尚未成功的全部筛查。 */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return CompensationHandlerResult.terminalFailure("CASE_SCREENING_TRANSACTION_ACTIVE", "Screening must run outside a transaction");
        }
        if (event.payloadJson() != null || !event.eventKey().matches("[1-9][0-9]{0,18}")) {
            return CompensationHandlerResult.terminalFailure("CASE_SCREENING_INVALID", "Screening event is malformed");
        }
        ClientReviewFamily family;
        try {
            family = families.load(Long.parseLong(event.eventKey()));
        } catch (FailException missing) {
            return CompensationHandlerResult.terminalFailure("CASE_SCREENING_INVALID", "Screening case is missing");
        }
        if (!DueDiligenceCaseStatus.ofCode(family.getMainCase().getStatus()).inProgress()) {
            return CompensationHandlerResult.success();
        }
        Map<Long, ScreeningSubject> subjects;
        try {
            subjects = directory.findByProfileIds(family.getSubjects().stream().map(CaseSubject::getSourceProfileId).distinct().toList());
        } catch (RuntimeException unavailable) {
            return CompensationHandlerResult.retryableFailure("CASE_SCREENING_SUBJECT_UNAVAILABLE", "Screening subjects are unavailable");
        }
        return runAll(family, subjects, event.subEventType());
    }

    /** 逐案件、逐模板执行；全部成功返回成功，否则可重试。 */
    CompensationHandlerResult runAll(ClientReviewFamily family, Map<Long, ScreeningSubject> subjects, String triggerRef) {
        boolean complete = true;
        for (DueDiligenceCase row : family.allCases()) {
            if (!DueDiligenceCaseStatus.ofCode(row.getStatus()).inProgress()) {
                continue;
            }
            CaseSubject subject = family.subjectOf(row.getCaseId());
            ScreeningSubject screened = subjects.get(subject.getSourceProfileId());
            if (screened == null) {
                return CompensationHandlerResult.terminalFailure("CASE_SCREENING_SUBJECT_MISSING", "Screening subject has no name");
            }
            for (ProgramScreening template : family.programOf(row).getScreenings()) {
                if (!succeeded(family, subject, template, triggerRef)) {
                    complete &= runOnce(row, subject, template, screened, triggerRef);
                }
            }
        }
        return complete ? CompensationHandlerResult.success()
            : CompensationHandlerResult.retryableFailure("CASE_SCREENING_INCOMPLETE", "Some screenings did not succeed");
    }

    /** 本次触发下该主体该模板是否已有成功执行。 */
    static boolean succeeded(ClientReviewFamily family, CaseSubject subject, ProgramScreening template, String triggerRef) {
        return family.getScreeningRuns().stream().anyMatch(run -> run.getCaseSubjectId() == subject.getCaseSubjectId()
            && run.getProgramScreeningId() == template.getProgramScreeningId() && triggerRef.equals(run.getTriggerRef())
            && ReviewCodes.RUN_SUCCEEDED.equals(run.getStatus()));
    }

    /**
     * 一次执行：短事务记 STARTED → 事务外调用渠道 → 短事务落结果
     *
     * @return 是否成功
     */
    boolean runOnce(DueDiligenceCase row, CaseSubject subject, ProgramScreening template, ScreeningSubject screened, String triggerRef) {
        String channel = properties.getChannelCode();
        ScreeningRun run = run(row, subject, template, channel, triggerRef);
        reviews.startScreeningRun(run);
        ScreeningSearchResult result = gateway.search(new ScreeningSearchCommand(channel, screened, template.getScreeningType(),
            run.getRequestRef()));
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        String status = switch (result.getOutcome()) {
            case ScreeningSearchResult.SUCCESS -> ReviewCodes.RUN_SUCCEEDED;
            case ScreeningSearchResult.FAIL -> ReviewCodes.RUN_FAILED;
            default -> ReviewCodes.RUN_TIMED_OUT;
        };
        List<ScreeningHit> hits = new ArrayList<>();
        List<ScreeningHitList> lists = new ArrayList<>();
        if (ReviewCodes.RUN_SUCCEEDED.equals(status)) {
            result.getHits().forEach(match -> hit(run, match, now, hits, lists));
        }
        reviews.completeScreeningRun(run, status, result.getRunRef(), hits, lists, now);
        return ReviewCodes.RUN_SUCCEEDED.equals(status);
    }

    /** 新执行行，请求引用为执行 ID。 */
    ScreeningRun run(DueDiligenceCase row, CaseSubject subject, ProgramScreening template, String channel, String triggerRef) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ScreeningRun run = new ScreeningRun();
        run.setScreeningRunId(ids.nextIdentifier());
        run.setCaseId(row.getCaseId());
        run.setCaseSubjectId(subject.getCaseSubjectId());
        run.setProgramScreeningId(template.getProgramScreeningId());
        run.setProviderCode(channel);
        run.setRequestRef(Long.toString(run.getScreeningRunId()));
        run.setTriggerSource(INITIAL_RUN.equals(triggerRef) ? "CASE" : "RERUN");
        run.setTriggerRef(triggerRef);
        run.setStatus(ReviewCodes.RUN_STARTED);
        run.setStartedTime(now);
        run.setVersion(1);
        run.setCreateTime(now);
        run.setUpdateTime(now);
        return run;
    }

    /** 一条命中及其名单类型；同一执行内重复的外部引用只记一次。 */
    void hit(ScreeningRun run, ScreeningSearchResult.Match match, Instant now, List<ScreeningHit> hits, List<ScreeningHitList> lists) {
        if (hits.stream().anyMatch(existing -> existing.getExternalHitRef().equals(match.getExternalRef()))) {
            return;
        }
        ScreeningHit hit = new ScreeningHit();
        hit.setHitId(ids.nextIdentifier());
        hit.setScreeningRunId(run.getScreeningRunId());
        hit.setExternalHitRef(match.getExternalRef());
        hit.setEntityType(match.getEntityType());
        hit.setMatchType(match.getMatchType());
        hit.setMatchScore(match.getScore());
        hit.setRiskLevel(match.getRiskLevel());
        hit.setStatus(ReviewCodes.HIT_OPEN);
        hit.setVersion(1);
        hit.setCreateTime(now);
        hit.setUpdateTime(now);
        hits.add(hit);
        match.getListTypes().stream().distinct().forEach(type -> {
            ScreeningHitList list = new ScreeningHitList();
            list.setHitListId(ids.nextIdentifier());
            list.setHitId(hit.getHitId());
            list.setListType(type);
            list.setCreateTime(now);
            lists.add(list);
        });
    }
}
