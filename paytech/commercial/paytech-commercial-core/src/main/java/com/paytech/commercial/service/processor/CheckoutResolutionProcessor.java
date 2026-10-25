package com.paytech.commercial.service.processor;

import static com.paytech.commercial.api.enums.CheckoutErrorCode.CCT_CONTEXT_CHANGED;
import static com.paytech.commercial.api.enums.CheckoutErrorCode.CLIENT_NOT_ELIGIBLE;
import static com.paytech.commercial.api.enums.CheckoutErrorCode.NO_ELIGIBLE_ENTITY;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.paytech.commercial.core.configuration.CommercialRelationshipSnapshotProperties;
import com.paytech.commercial.domain.CheckoutCandidateContext;
import com.paytech.commercial.domain.CheckoutContextView;
import com.paytech.commercial.domain.CheckoutQuery;
import com.paytech.commercial.domain.CheckoutResolutionContext;
import com.paytech.commercial.domain.ClientMasterCandidates;
import com.paytech.commercial.domain.relationship.QualificationSubject;
import com.paytech.commercial.domainservice.service.CheckoutQueryService;
import com.paytech.commercial.integration.CheckoutQualificationClient;
import com.paytech.commercial.integration.ClientMasterCheckoutClient;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 两段式只读解析编排（technique.md §3.1）：client-master 候选 → 本库商务快照 → compliance 一次批量资格判定 → Client 资格
 * → 逐候选构造、推导商业关系快照与组合约束 → OE / 提供方资格 → client-master 复核与本库重读。
 * <li>公共入口拒绝调用方事务；所有外部调用都在本库读事务之外，本库读事务内不调用外部服务。</li>
 * <li>Client 资格先于候选判定，即使没有候选：Client 不合格一律 CLIENT_NOT_ELIGIBLE，不被 NO_ELIGIBLE_ENTITY 掩盖（S-10）。</li>
 * <li>资格判定覆盖 Client 与全部主档候选的 OE、提供方，合并为一次往返；资格结果只在本次请求内使用，不进入候选相等性（S-13）。</li>
 * <li>候选按优先级惰性构造，任一条件不满足落到下一候选；client-master、compliance 或 verify 不可用一律 503 OWNER_UNAVAILABLE。</li>
 * <li>两次 client-master 调用沿用同一入站 Request ID（公共出站组件写入 Header），解析日志记录该 ID 与调用阶段。</li>
 */
@AllArgsConstructor
public class CheckoutResolutionProcessor extends AbstractProcessor<CheckoutQuery, CheckoutContextView> {
    private static final Logger LOG = LoggerFactory.getLogger(CheckoutResolutionProcessor.class);

    private final CheckoutQueryService queryService;
    private final ClientMasterCheckoutClient clientMaster;
    private final CheckoutQualificationClient qualificationClient;
    private final CommercialRelationshipSnapshotProperties relationshipSnapshot;

    @Override
    protected CheckoutContextView processRequest(CheckoutQuery query) {
        // 主档候选与本库快照，二者都在事务外取得，本库读事务内不调用外部 Owner。
        ClientMasterCandidates master = clientMaster.candidates(query);
        LOG.info("checkout 解析 client-master 候选 requestId={} clientId={} candidates={}", requestId(), query.clientId(),
            master.candidates().size());
        CheckoutResolutionContext context = load(query, master);

        // 一次批量资格判定；Client 不合格结束整次查询。
        Map<QualificationSubject, Boolean> qualifications = qualificationClient.evaluate(subjects(query, master));
        if (!qualifications.get(QualificationSubject.client(query.clientId()))) {
            throw new FailException(CLIENT_NOT_ELIGIBLE);
        }

        // 按优先级取第一个完整通过的候选。
        for (CheckoutCandidateContext candidate : context.candidates()) {
            if (!qualified(candidate, qualifications)) {
                continue;
            }

            // 主档复核与本库重读都通过后返回，由收单冻结。
            verify(query, master, context, candidate);
            return candidate.toView(relationshipSnapshot.effectiveModelVersion());
        }
        throw new FailException(NO_ELIGIBLE_ENTITY);
    }

    /** Client 与全部主档候选的 OE、提供方，按出现顺序去重。 */
    static List<QualificationSubject> subjects(CheckoutQuery query, ClientMasterCandidates master) {
        Set<QualificationSubject> subjects = new LinkedHashSet<>();
        subjects.add(QualificationSubject.client(query.clientId()));
        master.candidates().forEach(candidate -> subjects.addAll(CheckoutCandidateContext.qualificationSubjects(candidate)));
        return List.copyOf(subjects);
    }

    /** 候选的收单 OE 与提供方都合格；任一不合格记录日志并落到下一候选。 */
    boolean qualified(CheckoutCandidateContext candidate, Map<QualificationSubject, Boolean> qualifications) {
        for (QualificationSubject subject : candidate.qualificationSubjects()) {
            if (!qualifications.get(subject)) {
                LOG.info("checkout 候选落空：subject-not-eligible requestId={} clientId={} entityNo={} subjectType={}"
                    + " subjectId={}", requestId(), candidate.query().clientId(), candidate.entityNo(),
                    subject.subjectType().getCode(), subject.subjectId());
                return false;
            }
        }
        return true;
    }

    /**
     * 本库只读快照与合同参与方资料组成解析上下文
     * <li>资料由 client-master 读取，调用在本库读事务结束之后。</li>
     */
    CheckoutResolutionContext load(CheckoutQuery query, ClientMasterCandidates master) {
        var facts = queryService.load(master.scope());
        var profiles = clientMaster.partyProfiles(facts.partyProfileIds());
        return new CheckoutResolutionContext(query, master, facts, profiles);
    }

    /**
     * 外部资格检查后的一致性复核：client-master 复核主档根与选定授权，本库重读合同根与选定候选，任一变化即冲突。
     */
    void verify(CheckoutQuery query, ClientMasterCandidates master, CheckoutResolutionContext context,
        CheckoutCandidateContext candidate) {
        boolean unchanged = clientMaster.unchanged(query, master, candidate);
        LOG.info("checkout 解析 client-master 复核 requestId={} clientId={} entityNo={} unchanged={}", requestId(),
            query.clientId(), candidate.entityNo(), unchanged);
        if (!unchanged) {
            throw new FailException(CCT_CONTEXT_CHANGED);
        }
        context.verifyUnchanged(load(query, master), candidate);
    }

    /** 当前入站请求的 Request ID；公共出站组件以同一值调用 client-master。 */
    private static String requestId() {
        return MDC.get(ApiProblemProfile.REQUEST_ID_MDC_KEY);
    }
}
