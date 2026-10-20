package com.paytech.ledger.core.constant;

/** Ledger 写入口共用的 Redis 资源锁逻辑 Key；同一资源的所有写入口必须使用同一 Key（渠道成本锁已随 billing 迁出为 BillingLocks）。 */
public final class AccountingLocks {

    private AccountingLocks() {
    }

    /** 人工调整审批与驳回共用的资源锁。 */
    public static String manualAdjustment(long manualAdjustmentId) {
        return "manual-adjustment:" + manualAdjustmentId;
    }

    /** 同一科目表的科目维护与新建账簿（采用科目表）共用的资源锁：“检查未采用 → 修改结构”与“采用”互斥。 */
    public static String chart(long chartId) {
        return "chart:" + chartId;
    }

    /** 同一财务日历下创建期间的资源锁。 */
    public static String fiscalCalendar(long fiscalCalendarId) {
        return "fiscal-calendar:" + fiscalCalendarId;
    }

    /** 同一账簿同一期间执行试算的资源锁。 */
    public static String trialBalance(long ledgerBookId, long periodId) {
        return "trial-balance:" + ledgerBookId + ":" + periodId;
    }

    /** 同一期间关账发起、审批与驳回共用的资源锁。 */
    public static String periodClose(long periodId) {
        return "period-close:" + periodId;
    }

    /** 同一自动记账规则集的版本创建、提交、批准与驳回共用的资源锁。 */
    public static String accountingRuleBundle(String bundleCode) {
        return "accounting-rule-bundle:" + bundleCode;
    }

    /** 同一 account 过账流追平命令的资源锁；消费 Handler 不取锁，并发由 uk_instr_source 与只增进度保护。 */
    public static String postingStream(String aggregateId) {
        return "posting-stream:" + aggregateId;
    }
}
