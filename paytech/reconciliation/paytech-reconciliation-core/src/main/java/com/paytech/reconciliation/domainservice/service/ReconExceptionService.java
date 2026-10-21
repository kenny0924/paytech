package com.paytech.reconciliation.domainservice.service;

import java.util.List;

import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.domain.OpenReconExceptionCommand;

/**
 * 对账异常的开立与状态迁移（设计 F3 状态图；B 线提供，C 线复用）
 * <li>每个方法都在调用方事务内执行（无事务时自行开启本地事务），只写 t_recon_exception；状态迁移以版本与原状态为条件，冲突抛 409。</li>
 * <li>同一对象同一类型只有一个未结异常（OPEN、UNDER_REVIEW），由 active_slot 唯一键兜底。</li>
 */
public interface ReconExceptionService {

    /**
     * 开立异常；同一对象同一类型已有未结异常时返回该异常，不新建
     * <li>对账层与阻塞属性由类型派生：PLATFORM_ITEM_UNSETTLED、BANK_MOVEMENT_OVERDUE、BANK_STATEMENT_GAP 与全部银行层类型不阻塞。</li>
     * <li>opened_time 取当前时间，状态 OPEN。</li>
     *
     * @param command 异常类型、来源对象、主体、币种、差异金额与说明
     * @return 新开立或已有的未结异常
     */
    ReconException open(OpenReconExceptionCommand command);

    /**
     * 批量开立（匹配任务一次开立多条），逐条语义同 {@link #open}：已有未结异常的对象返回已有异常，其余按段批量插入
     * <li>同一批里同一对象同一类型重复出现时只开一条（以第一条命令为准）。</li>
     *
     * @param commands 开立命令
     * @return 与命令一一对应的新开立或已有的未结异常
     */
    List<ReconException> openAll(List<OpenReconExceptionCommand> commands);

    /**
     * 自动结案：同一对象同一类型的 OPEN 异常转为 AUTO_CLOSED（等待中被后续匹配自然消解、账单作废）
     * <li>没有未结异常或异常处于 UNDER_REVIEW（有待复核处置）时不变。</li>
     *
     * @param sourceType 挂靠对象类型
     * @param sourceId 挂靠对象 ID
     * @param exceptionType 异常类型
     * @return 是否结案了一条异常
     */
    boolean autoClose(ReconExceptionSourceType sourceType, Long sourceId, ReconExceptionType exceptionType);

    /**
     * 批量自动结案（匹配任务），逐条语义同 {@link #autoClose}：按给定异常的对象与类型重新读取未结异常，只结 OPEN 的
     *
     * @param exceptions 要结案的异常（取其挂靠对象类型、ID 与异常类型）
     * @return 实际结案条数
     */
    int autoCloseAll(List<ReconException> exceptions);

    /**
     * 提交处置后 OPEN → UNDER_REVIEW
     *
     * @param exception 已读取的异常，成功后同步状态与版本
     */
    void markUnderReview(ReconException exception);

    /**
     * 复核拒绝后 UNDER_REVIEW → OPEN
     *
     * @param exception 已读取的异常，成功后同步状态与版本
     */
    void reopen(ReconException exception);

    /**
     * 处置已应用后 UNDER_REVIEW → RESOLVED，写结案时间并释放 active_slot
     *
     * @param exception 已读取的异常，成功后同步状态、结案时间与版本
     */
    void resolve(ReconException exception);
}
