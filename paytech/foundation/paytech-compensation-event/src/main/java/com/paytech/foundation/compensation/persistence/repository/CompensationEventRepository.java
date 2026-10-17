package com.paytech.foundation.compensation.persistence.repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationScanRequest;
import com.paytech.foundation.compensation.core.observability.CompensationBacklogSnapshot;
import com.paytech.foundation.compensation.model.ClaimedCompensationEvent;
import com.paytech.foundation.compensation.model.CompensationEventRecord;

/**
 * 补偿事件仓储，封装服务自有补偿表的持久化操作。
 *
 * @author Codex
 */
public interface CompensationEventRepository {

    /**
     * 新增补偿事件。
     *
     * @param record
     *            补偿事件记录
     */
    void insert(CompensationEventRecord record);

    /**
     * 根据稳定事件身份查询补偿事件。
     *
     * @param request
     *            登记请求
     * @return 补偿事件，不存在时返回 {@code null}
     */
    CompensationEventRecord findByIdentity(CompensationEventRequest request);

    /**
     * 根据稳定事件身份加锁查询补偿事件。
     *
     * @param request
     *            登记请求
     * @return 补偿事件，不存在时返回 {@code null}
     */
    CompensationEventRecord findByIdentityForUpdate(CompensationEventRequest request);

    /**
     * 加锁查询待人工恢复的补偿事件。
     *
     * @param eventId
     *            补偿事件 ID
     * @return 补偿事件，不存在时返回 {@code null}
     */
    CompensationEventRecord findForRecovery(long eventId);

    /**
     * 原子获取一次补偿事件执行权。
     *
     * @param eventId
     *            补偿事件 ID
     * @param lockOwner
     *            本次执行的锁持有者
     * @param leaseDuration
     *            租约时长
     * @return 获取成功后的不可变执行凭证
     */
    Optional<ClaimedCompensationEvent> claim(long eventId, String lockOwner, Duration leaseDuration);

    /**
     * 删除已经成功执行的补偿事件。
     *
     * @param claimedEvent
     *            当前执行凭证
     * @return 是否成功删除
     */
    boolean completeSuccess(ClaimedCompensationEvent claimedEvent);

    /**
     * 查询当前分片可执行或可接管的补偿事件 ID。
     *
     * @param request
     *            扫描请求
     * @return 候选补偿事件 ID
     */
    List<Long> findCandidateIds(CompensationScanRequest request);

    /**
     * 查询补偿积压快照。
     *
     * @return 补偿积压快照
     */
    CompensationBacklogSnapshot backlogSnapshot();

    /**
     * 将本次执行结果更新为等待重试。
     *
     * @param claimedEvent
     *            当前执行凭证
     * @param retryDelay
     *            下次执行前的等待时间
     * @param errorCode
     *            错误码
     * @param errorSummary
     *            安全错误摘要
     * @return 是否成功更新
     */
    boolean markRetry(ClaimedCompensationEvent claimedEvent, Duration retryDelay, String errorCode,
        String errorSummary);

    /**
     * 将本次执行结果更新为最终失败。
     *
     * @param claimedEvent
     *            当前执行凭证
     * @param errorCode
     *            错误码
     * @param errorSummary
     *            安全错误摘要
     * @return 是否成功更新
     */
    boolean markFailed(ClaimedCompensationEvent claimedEvent, String errorCode, String errorSummary);

    /**
     * 按预期锁版本重新打开失败事件。
     *
     * @param eventId
     *            补偿事件 ID
     * @param expectedLockVersion
     *            预期锁版本
     * @return 是否成功重新打开
     */
    boolean reopenFailed(long eventId, long expectedLockVersion);
}
