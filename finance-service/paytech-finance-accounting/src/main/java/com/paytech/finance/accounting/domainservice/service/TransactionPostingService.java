package com.paytech.finance.accounting.domainservice.service;

import java.util.List;

import com.paytech.finance.accounting.domain.TransactionPostingCommand;
import com.paytech.finance.accounting.domain.view.PostedInstructionView;

/**
 * 交易自动记账引擎：按当前生效的 STANDARD_TRANSACTION 规则版本，把一条 PAYMENT 交易经济结果生成 AUTO 指令并过账
 * <li>必须在调用方已开启的本地事务内执行（与交易经济创建同一事务），只操作本库数据库。</li>
 * <li>同一交易经济结果重复调用直接返回已有指令与分录，不重算；唯一键 uk_instr_source 兜底并发重复。</li>
 * <li>任一输入、规则、账户、账簿或期间不满足时整体拒绝（Fail-Closed），由调用方事务回滚。</li>
 */
public interface TransactionPostingService {

    /**
     * 生成并过账交易自动记账指令
     *
     * @param command 交易经济结果主键与 MID
     * @return 按账簿主键升序的已过账指令
     */
    List<PostedInstructionView> post(TransactionPostingCommand command);
}
