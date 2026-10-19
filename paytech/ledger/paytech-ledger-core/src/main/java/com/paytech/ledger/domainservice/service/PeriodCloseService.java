package com.paytech.ledger.domainservice.service;

import java.util.List;
import java.util.Map;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.ledger.core.dal.dataobject.AccountingInstruction;
import com.paytech.ledger.core.dal.dataobject.CloseApproval;
import com.paytech.ledger.core.dal.dataobject.CloseStep;
import com.paytech.ledger.core.dal.dataobject.InstructionLine;
import com.paytech.ledger.core.dal.dataobject.LedgerAccount;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.core.dal.dataobject.PeriodCloseBook;
import com.paytech.ledger.domain.PeriodCloseContext;
import com.paytech.ledger.domain.PeriodReopenContext;
import com.paytech.ledger.domain.view.PeriodCloseDetailView;
import com.paytech.ledger.domain.view.PeriodCloseView;

/**
 * 期间关账与重开流程的本地保存、状态迁移与查询
 * <li>步骤执行由 Processor 编排；本服务只负责关账表与期间状态的原子写入，以及关账检查所需的读取。</li>
 * <li>锁顺序：期间行（排他）→ 各账簿序号行（共享，按账簿主键升序）。</li>
 */
public interface PeriodCloseService {

    /**
     * 发起关账（步骤 1）：一个事务内锁期间并复验 OPEN、插入流程根（占用活动槽）、期间 OPEN → CLOSING、
     * 按账簿主键升序以共享锁读取序号行冻结水位、插入账簿证据与 7 个步骤，并把步骤 1 置为 SUCCEEDED
     * <li>同一期间已有活动流程时按并发冲突拒绝。</li>
     */
    void start(PeriodCloseContext context);

    /** 步骤成功：写入 SUCCEEDED 与结果摘要。 */
    void succeedStep(CloseStep step, String resultSummary);

    /**
     * 步骤失败：一个事务内把步骤置为 FAILED、流程 FAILED 并释放活动槽、期间 CLOSING → OPEN
     * <li>已提交的结转分录保留；再次关账时这些科目本期净额已为零。</li>
     */
    void fail(PeriodCloseContext context, CloseStep step, String resultSummary);

    /** 读取账簿主体的本期损益账户（科目 + 主体 + NONE），不存在时按身份矩阵创建；必须加入调用方事务。 */
    LedgerAccount ensureResultAccount(LedgerBook book, long resultChartAccountId);

    /** 保存损益结转指令及其行（VALIDATED）；必须加入调用方事务。 */
    void saveProfitCloseInstruction(AccountingInstruction instruction, List<InstructionLine> lines);

    /** 结转分录过账后推进账簿证据水位并记录指令；必须加入调用方事务。 */
    void recordProfitClosePosting(PeriodCloseBook book, long journalSequence, long instructionId);

    /** 冻结账簿最终试算。 */
    void finalizeBook(PeriodCloseBook book, long finalTrialRunId);

    /** 冻结关账摘要：流程 RUNNING → PENDING_CHECK，并写入 Maker 审批记录。 */
    void freeze(PeriodCloseContext context, String closeHash, CloseApproval makerApproval);

    /**
     * 批准：一个事务内锁期间、复验全部账簿 FINALIZED 且最终试算成功水位一致、写 Checker 记录、
     * 流程 COMPLETED 并释放活动槽、期间 CLOSING → CLOSED
     */
    void approve(PeriodCloseContext context, CloseApproval checkerApproval);

    /** 驳回：一个事务内写 Checker 记录、流程 REJECTED 并释放活动槽、期间 CLOSING → OPEN。 */
    void reject(PeriodCloseContext context, CloseApproval checkerApproval);

    /**
     * 规划重开冲回：读取原关账各账簿结转指令的行并逐行借贷互换、金额不变
     *
     * @param reopensCloseId 原关账流程主键
     * @return 账簿主键（升序）→ 冲回行；原关账无结转指令的账簿为空列表
     */
    Map<Long, List<InstructionLine>> planReopenReversal(long reopensCloseId);

    /**
     * 发起重开：一个事务内锁期间并复验 CLOSED、分配序号并插入流程根（占用活动槽）、期间 CLOSED → REOPENING、
     * 按账簿主键升序以共享锁冻结水位、插入账簿证据与 3 个步骤、步骤 1—2 置 SUCCEEDED、
     * 冻结重开摘要使流程进入 PENDING_CHECK，并写入带同一摘要的 Maker 记录
     * <li>同一期间已有活动流程时按并发冲突拒绝。</li>
     */
    void startReopen(PeriodReopenContext context, CloseApproval makerApproval);

    /** 重开批准：排他锁期间并确认仍处于 REOPENING；必须加入调用方事务，且先于冲回过账调用。 */
    void lockReopeningPeriod(PeriodReopenContext context);

    /** 重开账簿冲回完成：记录冲回指令（无冲回行时为空）并置 FINALIZED，水位保持冻结值；必须加入调用方事务。 */
    void finalizeReopenBook(PeriodCloseBook book, Long instructionId);

    /**
     * 完成重开：步骤 3 SUCCEEDED、写 Checker 记录、流程 COMPLETED 并释放活动槽、期间 REOPENING → OPEN 且重开次数加一
     * <li>必须加入调用方事务，与冲回过账一起提交或回滚。</li>
     */
    void completeReopen(PeriodReopenContext context, CloseApproval checkerApproval, String postSummary);

    /** 驳回重开：一个事务内写 Checker 记录、步骤 3 SKIPPED、流程 REJECTED 并释放活动槽、期间 REOPENING → CLOSED，不生成分录。 */
    void rejectReopen(PeriodCloseContext context, CloseApproval checkerApproval);

    /** 读取期间与序号对应的流程（含账簿证据与步骤），不存在时业务拒绝。 */
    PeriodCloseContext loadForReview(long periodId, int closeNo);

    /** 关账步骤 2：统计期间账簿中尚未过账的指令。 */
    long countPendingInstructions(PeriodCloseContext context);

    /** 关账步骤 2：统计期间内借贷不平或无行的分录。 */
    long countUnbalancedEntries(PeriodCloseContext context);

    /** 关账步骤 2：统计待执行或可重试的补偿任务。 */
    long countPendingCompensationEvents();

    /** 按序号降序分页查询期间的关账流程。 */
    PagingResponseBody<PeriodCloseView> pageCloses(long periodId, PagingRequestBody paging);

    /** 查询关账流程详情；期间或流程不存在时业务拒绝。 */
    PeriodCloseDetailView getClose(long periodId, int closeNo);
}
