package com.paytech.ledger.domainservice.service;

import com.paytech.ledger.core.dal.dataobject.AccountingPeriod;
import com.paytech.ledger.core.dal.dataobject.FiscalCalendar;

/** 财务日历与会计期间的本地创建和状态流转。 */
public interface AccountingPeriodService {

    /** 保存已验证的财务日历；同主体已有日历时拒绝。 */
    void createFiscalCalendar(FiscalCalendar calendar);

    /**
     * 在锁定日历行的事务内校验日期不重叠后保存期间
     * <li>日历行排他锁是同一日历下期间日期不重叠的数据库哨兵。</li>
     */
    void createAccountingPeriod(AccountingPeriod period);

    /** 把 FUTURE 期间按版本 CAS 改为 OPEN；并发变化时失败。 */
    void openAccountingPeriod(AccountingPeriod period);
}
