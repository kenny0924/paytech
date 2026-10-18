package com.paytech.ledger.core.dal.dataobject;

import com.paytech.ledger.core.enums.EntrySide;
import java.math.BigDecimal;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_instruction_line：追加保存已解析到唯一统一记账账户的借贷指令行；全部账户必须属于 Instruction Header 指定的同一 Ledger Book。 */
@Getter
@Setter
public class InstructionLine {
    /**
     * 记账指令明细的服务侧雪花主键
     */
    private Long id;

    /**
     * 所属会计记账指令主键
     */
    private Long instructionId;

    /**
     * 同一记账指令内稳定且有序的明细序号
     */
    private Integer lineNo;

    /**
     * 自动指令行采用的自动记账规则行主键
     * <li>可为空。</li>
     */
    private Long accountingRuleLineId;

    /**
     * 人工指令行一一对应的已批准人工调整明细主键
     * <li>可为空。</li>
     */
    private Long manualAdjustmentLineId;

    /**
     * 该指令行准备写入且已冻结账簿、科目、核算对象和余额桶的本库账户
     */
    private Long accountId;

    /**
     * 指令行的借方或贷方方向
     * <li>允许值：DEBIT、CREDIT。</li>
     */
    private EntrySide entrySide;

    /**
     * 按币种精度舍入后且必须大于零的记账金额
     */
    private BigDecimal amount;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;
}
