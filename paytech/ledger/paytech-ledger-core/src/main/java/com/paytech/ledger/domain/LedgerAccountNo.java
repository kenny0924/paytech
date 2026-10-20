package com.paytech.ledger.domain;

import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.dal.dataobject.FinanceEntity;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.core.enums.LedgerEntityType;

/**
 * 总账账户号的组成：{主体段}-{币种}-{科目编号}-{序号}（2026-10-19 科目分级决定第 7 条）
 * <li>主体段：OE 账簿为 entityNo，Finance Entity 账簿为财务主体代码。</li>
 * <li>序号按账簿与科目由 t_account_no_sequence 分配，至少 4 位、不足补零；三条建户路径都用本类组装，保证格式一致。</li>
 */
public final class LedgerAccountNo {
    /** 序号最少位数。 */
    static final int SEQUENCE_WIDTH = 4;

    private final String prefix;

    private LedgerAccountNo(String prefix) {
        this.prefix = prefix;
    }

    /**
     * 由账簿、科目与（FE 账簿时的）财务主体确定账户号前缀
     *
     * @param book 账户所在账簿
     * @param chartAccount 账户使用的科目
     * @param financeEntity FE 账簿的财务主体；OE 账簿为空
     * @return 尚待分配序号的账户号
     */
    public static LedgerAccountNo of(LedgerBook book, ChartAccount chartAccount, FinanceEntity financeEntity) {
        String entitySegment = book.getEntityType() == LedgerEntityType.FINANCE_ENTITY ? financeEntity.getEntityCode()
            : book.getEntityId();
        return new LedgerAccountNo(entitySegment + "-" + book.getCurrency() + "-" + chartAccount.getAccountNo() + "-");
    }

    /** 拼接已分配的序号，至少 4 位、不足补零。 */
    public String withSequence(long sequence) {
        return prefix + String.format("%0" + SEQUENCE_WIDTH + "d", sequence);
    }
}
