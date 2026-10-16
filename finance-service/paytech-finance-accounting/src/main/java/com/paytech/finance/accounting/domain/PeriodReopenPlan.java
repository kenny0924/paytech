package com.paytech.finance.accounting.domain;

import java.util.ArrayList;
import java.util.List;

import com.paytech.finance.accounting.core.dal.dataobject.InstructionLine;
import com.paytech.finance.accounting.core.enums.EntrySide;

/**
 * 重开冲回行计算（纯计算）
 * <li>按原结转指令行的行号顺序逐行反向：借贷互换、账户与金额不变，不按当前余额重新计算。</li>
 * <li>返回的行只含行号、账户、方向与金额；主键、所属指令与创建时间在批准过账时补齐。</li>
 */
public final class PeriodReopenPlan {

    private PeriodReopenPlan() {
    }

    /**
     * 计算一条原结转指令的冲回行
     *
     * @param originalLines 原结转指令行，按行号升序
     * @return 冲回行，行号从 1 开始
     */
    public static List<InstructionLine> reverse(List<InstructionLine> originalLines) {
        List<InstructionLine> reversed = new ArrayList<>();
        for (InstructionLine original : originalLines) {
            InstructionLine line = new InstructionLine();
            line.setLineNo(reversed.size() + 1);
            line.setAccountId(original.getAccountId());
            line.setEntrySide(original.getEntrySide() == EntrySide.DEBIT ? EntrySide.CREDIT : EntrySide.DEBIT);
            line.setAmount(original.getAmount());
            reversed.add(line);
        }
        return reversed;
    }
}
