package com.paytech.finance.accounting.core.constant;

/** Accounting 写入口共用的 Redis 资源锁逻辑 Key；同一资源的所有写入口必须使用同一 Key。 */
public final class AccountingLocks {

    private AccountingLocks() {
    }

    /** 人工调整审批与驳回共用的资源锁。 */
    public static String manualAdjustment(long manualAdjustmentId) {
        return "manual-adjustment:" + manualAdjustmentId;
    }

    /** 同一财务日历下创建期间的资源锁。 */
    public static String fiscalCalendar(long fiscalCalendarId) {
        return "fiscal-calendar:" + fiscalCalendarId;
    }
}
