package com.paytech.ledger.core.dal.dataobject;

import com.paytech.ledger.core.enums.EntrySide;
import java.math.BigDecimal;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_manual_adjustment_line：保存人工会计调整中冻结的借贷明细，提交后只读并由人工记账指令逐行承接。 */
@Getter
@Setter
public class ManualAdjustmentLine {
    /**
     * 人工会计调整明细的服务侧雪花主键
     */
    private Long id;

    /**
     * 所属人工会计调整申请主键
     */
    private Long adjustmentId;

    /**
     * 同一人工调整内稳定且有序的明细序号
     */
    private Integer lineNo;

    /**
     * 人工调整实际使用且必须属于调整头账簿的本库统一账户
     */
    private Long accountId;

    /**
     * 人工调整明细的借方或贷方方向
     * <li>允许值：DEBIT、CREDIT。</li>
     */
    private EntrySide entrySide;

    /**
     * 按币种精度舍入且必须大于零的调整金额
     */
    private BigDecimal amount;

    /**
     * 不含敏感值的人工调整业务原因说明
     */
    private String lineDescription;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;

    /**
     * 该可变记录最后一次持久化更新的 UTC 微秒时间
     */
    private Instant updateTime;
}
