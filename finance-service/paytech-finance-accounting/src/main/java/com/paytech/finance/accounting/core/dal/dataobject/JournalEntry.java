package com.paytech.finance.accounting.core.dal.dataobject;

import com.paytech.finance.accounting.core.enums.JournalEntryType;
import java.time.Instant;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** t_journal_entry：追加保存一条记账指令在单一账簿和开放期间内形成的不可变会计分录头。 */
@Getter
@Setter
public class JournalEntry {
    /**
     * 会计分录的服务侧雪花主键
     */
    private Long id;

    /**
     * 该不可变分录所属的账簿主键
     */
    private Long ledgerBookId;

    /**
     * 分录过账日期所属且过账时必须开放的会计期间主键
     */
    private Long accountingPeriodId;

    /**
     * 产生该会计分录的已验证记账指令主键
     */
    private Long instructionId;

    /**
     * 在所属账簿内原子取得且严格递增的分录序号
     */
    private Long journalSequence;

    /**
     * 该会计分录所属的业务过账日期
     */
    private LocalDate postingDate;

    /**
     * 区分标准、冲回、调整和关账类分录的受控类型
     * <li>允许值：STANDARD、REVERSAL、ADJUSTMENT、CLOSING、REOPENING。</li>
     */
    private JournalEntryType entryType;

    /**
     * 冲回分录严格反向引用的原不可变会计分录主键
     * <li>可为空。</li>
     */
    private Long reversalOfEntryId;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;
}
