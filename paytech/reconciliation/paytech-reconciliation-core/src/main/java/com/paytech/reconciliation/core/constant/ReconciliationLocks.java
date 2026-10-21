package com.paytech.reconciliation.core.constant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * reconciliation 写入口共用的 Redis 资源锁逻辑 Key（technique D-11）；同一资源的所有写入口必须使用同一 Key
 * <li>全部入口经 {@code LockCommand.tryLocks} 获取同一形态的锁（单资源也用列表），避免单资源 SpinLock（lock: 前缀）与多资源 RLock 落在
 * 不同 Key 而失去互斥。</li>
 * <li>需要同时锁银行流水与预期银行流水时，先流水后预期，各自 ID 升序（C 线）。</li>
 */
public final class ReconciliationLocks {
    /**
     * 统一的锁等待预算
     * <li>匹配任务提交后立即被唤醒，可能与完成导入的释放重叠，等待 10 秒覆盖这一窗口；超时返回并发冲突。</li>
     */
    public static final Duration WAIT = Duration.ofSeconds(10);

    /**
     * 统一的有限锁租约
     * <li>须覆盖锁内最长处理：2 万行账单的逐行匹配与对平、挂 MID 前的 channel-connector 查询；租约失效时由版本条件 SQL 兜底。</li>
     */
    public static final Duration LEASE = Duration.ofMinutes(5);

    private ReconciliationLocks() {
    }

    /** 清算账户的配置修改、MID 挂入与摘除、启停共用的资源锁。 */
    public static List<String> clearingAccount(long clearingAccountId) {
        return List.of("clearing-account:" + clearingAccountId);
    }

    /** 渠道账单的分段追加、完成导入、匹配与对平、作废共用的资源锁。 */
    public static List<String> channelStatement(long statementId) {
        return List.of("channel-statement:" + statementId);
    }

    /** 银行流水资源锁 Key（确认匹配、手工匹配；C 线）。 */
    public static String bankStatementLine(long bankStatementLineId) {
        return "bank-statement-line:" + bankStatementLineId;
    }

    /** 预期银行流水资源锁 Key（确认匹配时排在银行流水之后；C 线）。 */
    public static String expectedBankMovement(long expectedMovementId) {
        return "expected-bank-movement:" + expectedMovementId;
    }

    /** 对账异常的处置提交与复核共用的资源锁（C 线）。 */
    public static List<String> reconException(long exceptionId) {
        return List.of("recon-exception:" + exceptionId);
    }

    /** 对账异常资源锁 Key（复核时与应用对象的锁一起获取；C 线）。 */
    public static String reconExceptionKey(long exceptionId) {
        return "recon-exception:" + exceptionId;
    }

    /** 银行对账单的分段追加与完成导入共用的资源锁（C 线）。 */
    public static List<String> bankStatement(long bankStatementId) {
        return List.of("bank-statement:" + bankStatementId);
    }

    /** 银行账户的申请关闭与复核共用的资源锁 Key（C 线）。 */
    public static String cashAccount(long cashAccountId) {
        return "cash-account:" + cashAccountId;
    }

    /**
     * 同一主体 + 币种 + 用途的银行账户范围锁 Key（批准新建时校验只有一个 ACTIVE；C 线）
     * <li>主体标识已由合同限定为字母数字，不含敏感值。</li>
     */
    public static String cashAccountScope(String entityType, String entityId, String currency, String purpose) {
        return "cash-account-scope:" + entityType + ":" + entityId + ":" + currency + ":" + purpose;
    }

    /** 同一银行账户的匹配建议任务互斥锁，避免并发任务重复出建议（C 线）。 */
    public static List<String> bankMatchSuggestion(long cashAccountId) {
        return List.of("bank-match-suggestion:" + cashAccountId);
    }

    /**
     * 确认银行匹配（确认建议、人工多选匹配、银行层 RE_MATCH）的锁集合：银行流水在前，预期银行流水按 ID 升序
     * <li>tryLocks 会再按完整 Key 的 ASCII 顺序统一排序，所有入口得到同一顺序。</li>
     *
     * @param bankStatementLineId 银行流水主键
     * @param expectedMovementIds 预期银行流水主键
     * @return 锁 Key 列表
     */
    public static List<String> bankMatch(long bankStatementLineId, Collection<Long> expectedMovementIds) {
        List<String> keys = new ArrayList<>();
        keys.add(bankStatementLine(bankStatementLineId));
        expectedMovementIds.stream().sorted().distinct().forEach(id -> keys.add(expectedBankMovement(id)));
        return keys;
    }
}
