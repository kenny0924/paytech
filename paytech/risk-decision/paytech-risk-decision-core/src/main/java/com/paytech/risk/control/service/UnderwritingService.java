package com.paytech.risk.control.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.paytech.risk.control.core.dal.dataobject.ClientUnderwriting;
import com.paytech.risk.control.domain.underwriting.UnderwritingContent;
import com.paytech.risk.control.domain.underwriting.UnderwritingCurrent;
import com.paytech.risk.control.domain.underwriting.UnderwritingDecisionContext;
import com.paytech.risk.control.domain.underwriting.UnderwritingInput;
import com.paytech.risk.control.domain.underwriting.UnderwritingListFilter;
import com.paytech.risk.control.domain.underwriting.UnderwritingPreview;
import com.paytech.risk.control.domain.underwriting.UnderwritingRule;
import com.paytech.risk.control.domain.underwriting.UnderwritingStatistics;
import com.paytech.risk.control.domain.underwriting.UnderwritingView;

/** Client 承保的本地保存与管理查询；调用方已完成归属、状态、版本与字段校验。 */
public interface UnderwritingService {
    /** 插入新草稿（含修订草稿）。 */
    void createDraft(ClientUnderwriting row);

    /** 以期望版本整体替换草稿内容。 */
    void saveDraft(ClientUnderwriting row, UnderwritingContent content, Instant now);

    /** HIGH 风险提交：冻结内容并置为 SUBMITTED。 */
    void submitForReview(UnderwritingDecisionContext context);

    /** 提交即决定或复核决定：批准时同一事务先替代上一有效版本。 */
    void decide(UnderwritingDecisionContext context);

    /** 自动承保：同一事务先替代上一有效版本，再插入 APPROVED 新行。 */
    void autoApprove(UnderwritingDecisionContext context);

    /** 读取 ACTIVE 自动承保规则及其阈值；未配置为空。 */
    Optional<UnderwritingRule> findActiveRule();

    /** 自动承保判定与估算敞口预览（纯计算，不写库）。 */
    UnderwritingPreview preview(UnderwritingInput input, Integer settlementDays);

    /** 读取单个版本。 */
    Optional<UnderwritingView> findView(long underwritingId);

    /** 读取 Client 当前的有效、进行中与最新版本。 */
    UnderwritingCurrent findCurrent(long clientId);

    /** 读取 Client 的版本历史，版本号倒序，最多 100 个。 */
    List<UnderwritingView> findHistory(long clientId);

    /** 按筛选条件分页读取版本及总数。 */
    Page<UnderwritingView> findPage(UnderwritingListFilter filter, int limit, long offset);

    /** 列表顶部统计，"本月"取 now 所在 UTC 自然月。 */
    UnderwritingStatistics statistics(Instant now);

    /**
     * 一页查询结果
     *
     * @param items 本页条目
     * @param total 同一条件下的总数
     * @param <T> 条目类型
     */
    record Page<T>(List<T> items, long total) { }
}
