package com.paytech.finance.accounting.core.dal.dataobject;

import com.paytech.finance.accounting.core.enums.EntrySide;
import java.math.BigDecimal;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_journal_line：追加保存会计分录中不可变的借贷行，并通过统一账户保持账簿、科目、核算对象和余额桶可追溯。 */
@Getter
@Setter
public class JournalLine {
    /**
     * 会计分录明细的服务侧雪花主键
     */
    private Long id;

    /**
     * 所属不可变会计分录主键
     */
    private Long journalEntryId;

    /**
     * 同一会计分录内稳定且有序的明细序号
     */
    private Integer lineNo;

    /**
     * 生成该分录明细的记账指令明细主键
     */
    private Long instructionLineId;

    /**
     * 该分录明细实际记入且必须属于分录头账簿的本库统一账户
     */
    private Long accountId;

    /**
     * 该分录明细的借方或贷方方向
     * <li>允许值：DEBIT、CREDIT。</li>
     */
    private EntrySide entrySide;

    /**
     * 按币种精度舍入且必须大于零的不可变分录金额
     */
    private BigDecimal amount;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;
}
