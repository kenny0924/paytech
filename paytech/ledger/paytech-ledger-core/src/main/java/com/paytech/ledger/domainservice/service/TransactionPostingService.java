package com.paytech.ledger.domainservice.service;

import java.util.List;

import com.paytech.ledger.domain.TransactionPostingCommand;
import com.paytech.ledger.domain.view.PostedInstructionView;

/**
 * 交易自动记账引擎：按当前生效的 STANDARD_TRANSACTION 规则版本，把一条 PAYMENT 经济事实生成 AUTO 指令并过账
 * <li>输入驱动：经济事实（头 + 明细行 + 渠道清算账户主键）来自 EconomicFactRecorded 事件 Payload，事务内只读写本库表。</li>
 * <li>必须在调用方已开启的本地事务内执行（生产调用方是 paytech-messaging 消费拦截器的事务），只操作本库数据库；不写任何余额。</li>
 * <li>同一经济事实重复调用直接返回已有指令与分录，不重算；唯一键 uk_instr_source 兜底并发重复。</li>
 * <li>事实不完整或规则、账户、账簿、期间不满足时整体拒绝（Fail-Closed），由调用方事务回滚；Partner 登记不变量由 billing 负责。</li>
 */
public interface TransactionPostingService {

    /**
     * 生成并过账交易自动记账指令
     *
     * @param command 经济事实载荷
     * @return 按账簿主键升序的已过账指令
     */
    List<PostedInstructionView> post(TransactionPostingCommand command);
}
