package com.paytech.compliance.domainservice.service;

import java.time.Instant;
import java.util.List;

import com.paytech.compliance.core.dal.dataobject.CaseClassification;
import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.compliance.core.dal.dataobject.EvidenceRef;
import com.paytech.compliance.domain.ClientReviewContext;
import com.paytech.compliance.domain.ClientReviewDecisionContext;
import com.paytech.compliance.domain.ReviewRequirementView;

/**
 * Client 入驻审核的本地保存；不访问外部服务
 * <li>审核命令都先以版本条件递增主任务版本，同一任务的命令因此串行；版本已变化抛出并发冲突并整体回滚。</li>
 */
public interface ClientReviewService {
    /**
     * 在一个本地事务中保存全部案件、主体、要求、证据、依赖与初始状态变更
     * <li>唯一键冲突以 DuplicateKeyException 抛出并整体回滚，由调用方判断是重放还是冲突。</li>
     */
    void register(ClientReviewContext context);

    /**
     * 保存一份材料的核验结果，并按需同步材料要求状态
     *
     * @param mainCase 主任务，读取时版本
     * @param evidence 证据，读取时版本且仍为 RECEIVED
     * @param outcome ACCEPTED 或 REJECTED
     * @param requirement 证据所属材料要求
     * @param requirementStatus 要求的新状态；不变时为空
     * @param now 服务端时间
     */
    void reviewEvidence(DueDiligenceCase mainCase, EvidenceRef evidence, String outcome, ReviewRequirementView requirement,
        String requirementStatus, Instant now);

    /**
     * 保存 CCT 已确认的分类集合
     * <li>唯一键冲突以 DuplicateKeyException 抛出并整体回滚，由调用方按已接收集合判断重放。</li>
     */
    void saveClassifications(DueDiligenceCase mainCase, List<CaseClassification> classifications, Instant now);

    /**
     * 结束主任务与子任务，保存决定和状态变更，并登记结果发布任务
     * <li>结果在提交后由补偿任务发布给 CCT；事务内不访问 Broker。</li>
     */
    void decide(ClientReviewDecisionContext context);
}
