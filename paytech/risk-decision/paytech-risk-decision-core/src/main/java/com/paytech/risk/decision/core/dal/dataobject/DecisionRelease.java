package com.paytech.risk.decision.core.dal.dataobject;

import java.time.Instant;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

/** 一个事件点和环境的不可变组成及可变发布状态根。 */
@Getter
@AllArgsConstructor
@Accessors(fluent = true)
public class DecisionRelease {
    /** 决策发布的稳定业务标识。 */
    private final long releaseId;

    /** 全局唯一的规范正十进制发布编号文本。 */
    @NonNull
    private final String releaseNo;

    /** 被发布决策事件点的稳定业务标识。 */
    private final long decisionPointId;

    /** 被冻结事件点版本的稳定业务标识。 */
    private final long pointVersionId;

    /** 发布所属环境。 */
    @NonNull
    private final DecisionEnvironment environmentCode;

    /** 发布状态。 */
    @NonNull
    private DecisionReleaseStatus status;

    /** ACTIVE 发布占用值；其他状态为空。 */
    private final Boolean activeSlot;

    /** 发布完整组成的 SHA-256 小写十六进制哈希。 */
    @NonNull
    private final String contentHash;

    /** 创建人的稳定业务标识。 */
    private final long createdBy;

    /** 激活 UTC 微秒时间；尚未激活时为空。 */
    private final Instant activatedTime;

    /** 被替代 UTC 微秒时间；尚未替代时为空。 */
    private final Instant supersededTime;

    /** 发布聚合的并发版本。 */
    private int version;

    /** 记录的 UTC 微秒创建时间。 */
    @NonNull
    private final Instant createTime;

    /** 发布根最后一次持久化更新的 UTC 微秒时间。 */
    @NonNull
    private Instant updateTime;

    /** 验证 TEST Candidate 根及状态版本矩阵。 */
    public void verifyIntegrity() {
        if (releaseId() <= 0 || decisionPointId() <= 0
            || pointVersionId() <= 0 || createdBy() <= 0
            || environmentCode() != DecisionEnvironment.TEST || activeSlot() != null
            || activatedTime() != null || supersededTime() != null
            || createTime() == null || updateTime() == null
            || (contentHash == null || !contentHash.matches("[0-9a-f]{64}"))
            || status() == DecisionReleaseStatus.DRAFT && version() != 1
            || status() == DecisionReleaseStatus.VALIDATING && version() != 2
            || status() == DecisionReleaseStatus.REJECTED && version() != 3
            || status() != DecisionReleaseStatus.DRAFT
                && status() != DecisionReleaseStatus.VALIDATING
                && status() != DecisionReleaseStatus.REJECTED) {
            throw new ErrorException("Decision Release Root 不符合首期合同");
        }
        canonicalReleaseNo();
    }

    /** 返回受控的正整数发布编号。 */
    public int canonicalReleaseNo() {
        String value = releaseNo;
        if (value == null || !value.matches("[1-9][0-9]{0,9}")) {
            throw new ErrorException("Release No 不是规范正整数");
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new ErrorException("Release No 超出首期整数范围", exception);
        }
    }

    /** 首次校验只接受调用方读取的 DRAFT 原版本。 */
    public void verifyValidationStart(int expectedVersion) {
        verifyIntegrity();
        if (version != expectedVersion) {
            throw new ConcurrentRequestException("Candidate 聚合版本已变化");
        }
        if (status != DecisionReleaseStatus.DRAFT || version != 1) {
            throw new BizCheckFailException("只有 DRAFT/version=1 Candidate 可以开始静态校验");
        }
    }

    /** 仅在条件 SQL 成功后同步当前根，事务失败时丢弃本次对象。 */
    public void applyValidationStarted(Instant time) {
        status = DecisionReleaseStatus.VALIDATING;
        version = 2;
        updateTime = time;
    }

    /** 仅在拒绝状态 CAS 成功后同步当前根。 */
    public void applyValidationRejected(Instant time) {
        status = DecisionReleaseStatus.REJECTED;
        version = 3;
        updateTime = time;
    }

}
