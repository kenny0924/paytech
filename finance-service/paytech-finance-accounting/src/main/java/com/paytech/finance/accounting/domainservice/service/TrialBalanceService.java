package com.paytech.finance.accounting.domainservice.service;

import com.paytech.finance.accounting.core.dal.dataobject.AccountingPeriod;
import com.paytech.finance.accounting.core.dal.dataobject.LedgerBook;
import com.paytech.finance.accounting.domain.TrialBalanceContext;
import com.paytech.finance.accounting.domain.view.TrialBalanceDetailView;
import com.paytech.finance.accounting.domain.view.TrialBalanceRunView;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

/** 试算平衡的执行与查询。 */
public interface TrialBalanceService {

    /**
     * 在一个本地事务内执行试算并保存终态
     * <li>先以共享锁读取账簿序号行确定水位，等待在途过账提交，避免漏读已取号未提交的分录。</li>
     * <li>结果写回 Context；只有 SUCCEEDED 才刷新期间余额投影。</li>
     *
     * @param context 已校验的账簿、期间与币种精度
     */
    void run(TrialBalanceContext context);

    /**
     * 按调用方给定的水位执行试算（关账流程使用）
     * <li>水位必须是调用方已确认全部提交的序号，本方法不再加序号行锁。</li>
     * <li>账户投影核对跳过最后序号已超过该水位的账户：它们已包含水位之后其他期间的过账，无法在此水位比较。</li>
     *
     * @param context 已校验的账簿、期间与币种精度
     * @param asOfSequence 试算纳入的最大分录序号
     */
    void runAt(TrialBalanceContext context, long asOfSequence);

    /** 读取账簿与期间，均存在时返回账簿；不存在时业务拒绝。 */
    LedgerBook getBook(long ledgerBookId);

    /** 读取期间，不存在时业务拒绝。 */
    AccountingPeriod getPeriod(long periodId);

    /** 按序号降序分页查询试算摘要；账簿或期间不存在时拒绝。 */
    PagingResponseBody<TrialBalanceRunView> pageRuns(long ledgerBookId, long periodId, PagingRequestBody paging);

    /** 查询单次试算详情；账簿、期间或试算不存在时拒绝。 */
    TrialBalanceDetailView getRun(long ledgerBookId, long periodId, int runNo);
}
