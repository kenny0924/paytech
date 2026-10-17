package com.paytech.commercial.service.processor;

import static com.paytech.commercial.api.enums.CheckoutErrorCode.CCT_CONTEXT_CHANGED;
import static com.paytech.commercial.api.enums.CheckoutErrorCode.NO_ELIGIBLE_ENTITY;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.paytech.commercial.domain.CheckoutCandidateContext;
import com.paytech.commercial.domain.CheckoutContextView;
import com.paytech.commercial.domain.CheckoutQuery;
import com.paytech.commercial.domain.CheckoutResolutionContext;
import com.paytech.commercial.domain.ClientMasterCandidates;
import com.paytech.commercial.domainservice.service.CheckoutQueryService;
import com.paytech.commercial.integration.CheckoutQualificationClient;
import com.paytech.commercial.integration.ClientMasterCheckoutClient;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 两段式只读解析编排（design/06 §2.2）：client-master 候选 → 本库商务快照逐候选解析 → Risk 资格 → client-master 复核与本库重读。
 * <li>公共入口拒绝调用方事务；所有外部调用都在本库读事务之外，本库读事务内不调用外部服务。</li>
 * <li>候选按优先级惰性构造，fall-through 语义与原单库实现一致；client-master 或 verify 不可用一律 503 OWNER_UNAVAILABLE。</li>
 * <li>两次 client-master 调用沿用同一入站 Request ID（公共出站组件写入 Header），解析日志记录该 ID 与调用阶段。</li>
 */
@AllArgsConstructor
public class CheckoutResolutionProcessor extends AbstractProcessor<CheckoutQuery, CheckoutContextView> {
    private static final Logger LOG = LoggerFactory.getLogger(CheckoutResolutionProcessor.class);

    private final CheckoutQueryService queryService;
    private final ClientMasterCheckoutClient clientMaster;
    private final CheckoutQualificationClient qualificationClient;

    @Override
    protected CheckoutContextView processRequest(CheckoutQuery query) {
        // 主档候选与本库快照，二者都在事务外取得，本库读事务内不调用外部 Owner。
        ClientMasterCandidates master = clientMaster.candidates(query);
        LOG.info("checkout 解析 client-master 候选 requestId={} clientId={} candidates={}", requestId(), query.clientId(),
            master.candidates().size());
        CheckoutResolutionContext context = load(query, master);
        var candidates = context.candidates().iterator();
        if (!candidates.hasNext()) {
            throw new FailException(NO_ELIGIBLE_ENTITY);
        }

        // Client 级拒绝结束整次查询，明确候选不合格才继续。
        qualificationClient.requireClientEligible(query.clientId());
        while (candidates.hasNext()) {
            CheckoutCandidateContext candidate = candidates.next();
            if (!qualificationClient.isCandidateEligible(candidate.entityNo(), candidate.localPartnerId())) {
                continue;
            }

            // 主档复核与本库重读都通过后返回，由 Commerce 冻结。
            verify(query, master, context, candidate);
            return candidate.toView();
        }
        throw new FailException(NO_ELIGIBLE_ENTITY);
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
