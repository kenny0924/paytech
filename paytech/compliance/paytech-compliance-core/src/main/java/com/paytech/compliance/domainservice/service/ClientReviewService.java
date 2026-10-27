package com.paytech.compliance.domainservice.service;

import java.time.Instant;
import java.util.List;

import com.paytech.compliance.core.dal.dataobject.CaseClassification;
import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.compliance.core.dal.dataobject.ScreeningHit;
import com.paytech.compliance.core.dal.dataobject.ScreeningHitList;
import com.paytech.compliance.core.dal.dataobject.ScreeningRun;
import com.paytech.compliance.domain.CaseTransitionContext;
import com.paytech.compliance.domain.CheckResultContext;
import com.paytech.compliance.domain.ClientReviewContext;
import com.paytech.compliance.domain.ClientReviewDecisionContext;
import com.paytech.compliance.domain.EvidenceReviewContext;
import com.paytech.compliance.domain.HitResolutionContext;
import com.paytech.compliance.domain.ReviewCommandContext;
import com.paytech.compliance.domain.ReviewRequirementView;
import com.paytech.compliance.domain.RiskRatingContext;

/**
 * Client 入驻审核的本地原子保存；全部方法只访问本库，不调用外部服务
 * <li>审核命令先推进或递增主案件版本，使同一案件的命令串行；版本已变化时并发冲突并整体回滚。</li>
 * <li>未说明的方法各自开启 READ COMMITTED、5 秒超时的短事务；调用方已开启事务时加入该事务。</li>
 */
public interface ClientReviewService {
    /** 保存全部登记事实并登记 CASE_SCREENING 筛查任务。 */
    void register(ClientReviewContext context);

    /** 写入材料核验结果，要求状态变化时一并更新。 */
    void reviewEvidence(EvidenceReviewContext context);

    /** 豁免一项材料要求。 */
    void waive(ReviewCommandContext command, ReviewRequirementView requirement, String reasonCode, String note);

    /** 录入人工检查结论。 */
    void recordCheck(CheckResultContext context);

    /** 追加命中处置，处置关闭命中时一并关闭。 */
    void resolveHit(HitResolutionContext context);

    /** 替换手工评级与审核员因子，并清空推荐。 */
    void rateRisk(RiskRatingContext context);

    /** 写入推荐。 */
    void recommend(ReviewCommandContext command, String recommendation, String note);

    /** 登记一次重跑筛查任务。 */
    void requestScreening(ReviewCommandContext command, String rerunRef);

    /** 递增主案件版本后插入全部分类。 */
    void saveClassifications(DueDiligenceCase mainCase, List<CaseClassification> rows, Instant now);

    /**
     * 结束主案件与子案件、写入决定与退回错误、追加状态变更并登记结果发布
     * <li>不处理资格投影；批准或拒绝时由调用方在同一事务内调用 QualificationService#applyProjection。</li>
     */
    void decide(ClientReviewDecisionContext context);

    /** 撤回或过期：迁移一组案件并追加状态变更，过期时登记结果发布。 */
    void transition(CaseTransitionContext context);

    /** 新建一次 STARTED 的筛查执行。 */
    void startScreeningRun(ScreeningRun run);

    /**
     * 结束一次筛查执行；成功时写入命中与名单类型，并取代同一主体同一模板其他执行的命中
     *
     * @param run 读取时的执行
     * @param status SUCCEEDED、FAILED 或 TIMED_OUT
     * @param executionRef 提供方执行引用
     * @param hits 命中；非成功为空列表
     */
    void completeScreeningRun(ScreeningRun run, String status, String executionRef, List<ScreeningHit> hits,
        List<ScreeningHitList> hitLists, Instant now);
}
