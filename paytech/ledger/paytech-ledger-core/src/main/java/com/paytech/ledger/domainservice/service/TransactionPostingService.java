package com.paytech.ledger.domainservice.service;

import java.util.List;

import com.paytech.ledger.domain.AccountPostingCommand;
import com.paytech.ledger.domain.view.PostedInstructionView;

/**
 * 控制科目过账引擎（技术方案 2.1）：按一个 account 入账批次生成 ACCOUNT_POSTING 指令并过账
 * <li>Owner 控制科目行逐条来自批次流水（记到账簿主体的控制账户，account_entry_id 指回流水）；非 Owner 行由规则引擎推导：PAYMENT 用
 * STANDARD_TRANSACTION，MANUAL_ADJUSTMENT / COST_ADJUSTMENT 用 STANDARD_ADJUSTMENT；替代事实在同一指令内对被替代事实的规则行取反。</li>
 * <li>输入在事务外准备：批次来自 AccountPosted 载荷或 account-api，计费事实来自 billing-api；事务内只读写本库表，不写任何余额。</li>
 * <li>必须在调用方已开启的本地事务内执行（消费 Handler 或追平命令的事务）。</li>
 * <li>同一批次重复调用直接返回已有指令与分录；唯一键 uk_instr_source（account、批次主键、指令类型）兜底并发重复。</li>
 * <li>事实不完整或规则、账户、账簿不满足时业务拒绝；没有可用 OPEN 期间时以并发冲突等待，均由调用方事务回滚。</li>
 */
public interface TransactionPostingService {

    /**
     * 生成并过账一个入账批次的指令
     *
     * @param command 需要过账的入账批次与版本一致的计费事实
     * @return 按账簿主键升序的已过账指令；批次没有任何可过账行时为空列表
     */
    List<PostedInstructionView> post(AccountPostingCommand command);
}
