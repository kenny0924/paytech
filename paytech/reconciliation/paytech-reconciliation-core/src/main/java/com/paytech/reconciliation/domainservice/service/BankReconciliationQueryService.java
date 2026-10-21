package com.paytech.reconciliation.domainservice.service;

import java.time.LocalDate;
import java.util.List;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.reconciliation.api.enums.AccountPurpose;
import com.paytech.reconciliation.api.enums.BankMatchStatus;
import com.paytech.reconciliation.api.enums.CashAccountStatus;
import com.paytech.reconciliation.api.enums.ReconEntityType;
import com.paytech.reconciliation.api.enums.BankStatementStatus;
import com.paytech.reconciliation.api.enums.ExpectedMovementStatus;
import com.paytech.reconciliation.api.enums.ExpectedMovementType;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.MatchMethod;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.core.dal.dataobject.BankMatch;
import com.paytech.reconciliation.core.dal.dataobject.BankStatement;
import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;
import com.paytech.reconciliation.domain.BankMatchContext;
import com.paytech.reconciliation.domain.view.BankStatementLineDetailView;

/**
 * 银行层查询（UC-R17，#10、#11、#19–#21）与确认匹配上下文的装载；只读，不开启事务
 * <li>装载上下文供确认建议、人工多选匹配与银行层 RE_MATCH 处置在锁内使用。</li>
 */
public interface BankReconciliationQueryService {

    /**
     * 在锁内装载确认匹配上下文：银行流水、所属对账单、各预期流水及渠道来源账单
     *
     * @param bankStatementLineId 银行流水主键
     * @param allocations 分配（预期流水主键与金额、可选原建议）
     * @param matchMethod 新建匹配的方式
     * @param operatorId 操作人
     * @param resolutionId RE_MATCH 处置主键；其他为空
     * @return 上下文（尚未校验）；流水或预期流水不存在时业务拒绝
     */
    BankMatchContext loadMatchContext(Long bankStatementLineId, List<BankMatchContext.AllocationInput> allocations,
        MatchMethod matchMethod, Long operatorId, Long resolutionId);

    /**
     * 读取银行账户，不存在为业务拒绝
     *
     * @param cashAccountId 银行账户主键
     * @return 银行账户
     */
    CashAccount getCashAccount(long cashAccountId);

    /**
     * 当前 ACTIVE 银行账户（#11）
     *
     * @param entityType 主体类型
     * @param entityId 主体标识
     * @param currency 币种
     * @param purpose 用途
     * @return 银行账户；没有时业务拒绝
     */
    CashAccount getCurrentCashAccount(ReconEntityType entityType, String entityId, String currency,
        AccountPurpose purpose);

    /**
     * 分页查询银行账户（更新时间降序）
     *
     * @param entityType 可选主体类型
     * @param entityId 可选主体标识
     * @param currency 可选币种
     * @param purpose 可选用途
     * @param status 可选状态
     * @param paging 已校验的分页参数
     * @return 分页结果
     */
    PagingResponseBody<CashAccount> listCashAccounts(ReconEntityType entityType, String entityId, String currency,
        AccountPurpose purpose, CashAccountStatus status, PagingRequestBody paging);

    /**
     * 读取银行对账单，不存在为业务拒绝
     *
     * @param bankStatementId 对账单主键
     * @return 对账单
     */
    BankStatement getBankStatement(long bankStatementId);

    /**
     * 按主键读取银行对账单（详情页）
     *
     * @param bankStatementId 对账单主键
     * @return 对账单；不存在时 404 BANK_STATEMENT_NOT_FOUND
     */
    BankStatement readBankStatement(long bankStatementId);

    /**
     * 按主键读取银行流水详情：所属对账单与银行账户、全部银行匹配（含预期银行流水）与流水上的未结异常
     *
     * @param bankStatementLineId 银行流水主键
     * @return 详情；不存在时 404 BANK_STATEMENT_LINE_NOT_FOUND
     */
    BankStatementLineDetailView readBankStatementLine(long bankStatementLineId);

    /**
     * 分页查询银行对账单
     *
     * @param cashAccountId 可选银行账户
     * @param status 可选状态
     * @param paging 已校验的分页参数
     * @return 分页结果
     */
    PagingResponseBody<BankStatement> listBankStatements(Long cashAccountId, BankStatementStatus status,
        PagingRequestBody paging);

    /**
     * 分页查询银行流水（指定对账单时按行号升序，否则按起息日升序）
     *
     * @param statementId 可选对账单
     * @param cashAccountId 可选银行账户
     * @param matchStatus 可选匹配状态
     * @param direction 可选方向
     * @param valueDateFrom 可选起息日起
     * @param valueDateTo 可选起息日止
     * @param paging 已校验的分页参数
     * @return 分页结果
     */
    PagingResponseBody<BankStatementLine> listBankStatementLines(Long statementId, Long cashAccountId,
        LineMatchStatus matchStatus, SettlementDirection direction, LocalDate valueDateFrom, LocalDate valueDateTo,
        PagingRequestBody paging);

    /**
     * 分页查询预期银行流水
     *
     * @param cashAccountId 可选银行账户
     * @param movementType 可选来源类型
     * @param sourceId 可选来源 ID
     * @param status 可选状态
     * @param paging 已校验的分页参数
     * @return 分页结果
     */
    PagingResponseBody<ExpectedBankMovement> listExpectedMovements(Long cashAccountId, ExpectedMovementType movementType,
        Long sourceId, ExpectedMovementStatus status, PagingRequestBody paging);

    /**
     * 分页查询银行匹配（流水或预期流水至少一个）
     *
     * @param bankStatementLineId 可选银行流水
     * @param expectedMovementId 可选预期流水
     * @param status 可选状态
     * @param paging 已校验的分页参数
     * @return 分页结果
     */
    PagingResponseBody<BankMatch> listBankMatches(Long bankStatementLineId, Long expectedMovementId,
        BankMatchStatus status, PagingRequestBody paging);

    /**
     * 一条匹配所属银行账户的币种（响应金额补齐）
     *
     * @param match 银行匹配
     * @return 币种
     */
    String currencyOf(BankMatch match);
}
