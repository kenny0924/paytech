package com.paytech.risk.decision.core.dal.dataobject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.ReleaseApprovalStatus;
import com.paytech.risk.decision.domain.enums.ReleaseAuditEventType;
import com.paytech.risk.decision.domain.enums.StaticCheckSeverity;
import com.paytech.risk.decision.domain.enums.StaticCheckStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

/** 发布静态检查、审批或状态迁移的不可变审计事件。 */
@Getter
@AllArgsConstructor
@Accessors(fluent = true)
public class ReleaseAuditEvent {
    /** 发布审计事件的稳定业务标识。 */
    private final long auditEventId;

    /** 所属决策发布的稳定业务标识。 */
    private final long releaseId;

    /** 审计事件的闭合类型。 */
    @NonNull
    private final ReleaseAuditEventType eventType;

    /** 事件发生的 UTC 微秒时间。 */
    @NonNull
    private final Instant eventTime;

    /** STATIC_CHECK 的开放检查编码；其他事件为空。 */
    private final String checkCode;

    /** STATIC_CHECK 的检查状态；其他事件为空。 */
    private final StaticCheckStatus checkStatus;

    /** STATIC_CHECK 的严重程度；其他事件为空。 */
    private final StaticCheckSeverity severity;

    /** STATIC_CHECK 失败的开放错误编码；通过或其他事件为空。 */
    private final String errorCode;

    /** STATIC_CHECK 失败的脱敏摘要；通过或其他事件为空。 */
    private final String errorSummary;

    /** APPROVAL 关联的 Shadow Run；首期观察到非空即失败关闭。 */
    private final Long shadowRunId;

    /** APPROVAL 请求标识；其他事件为空。 */
    private final String approvalRequestId;

    /** APPROVAL 状态；其他事件为空。 */
    private final ReleaseApprovalStatus approvalStatus;

    /** APPROVAL 审核人；其他事件为空。 */
    private final Long reviewerId;

    /** APPROVAL 审核意见；其他事件为空。 */
    private final String reviewComment;

    /** STATE_CHANGE 的原状态；首次创建事件为空。 */
    private final DecisionReleaseStatus fromStatus;

    /** STATE_CHANGE 的新状态；其他事件为空。 */
    private final DecisionReleaseStatus toStatus;

    /** STATE_CHANGE 的开放原因编码；其他事件为空。 */
    private final String reasonCode;

    /** STATE_CHANGE 的开放触发类型；其他事件为空。 */
    private final String triggerType;

    /** STATE_CHANGE 的不可变触发标识；其他事件为空。 */
    private final String triggerId;

    /** STATE_CHANGE 的操作人；系统事件可按合同为空。 */
    private final Long actorId;

    /** STATE_CHANGE 对应的聚合版本；其他事件为空。 */
    private final Integer aggregateVersion;

    /** 记录的 UTC 微秒创建时间。 */
    @NonNull
    private final Instant createTime;

    /** 新审计采用发布身份与业务阶段；恢复直接沿用已持久化的原触发标识。 */
    public static String triggerId(long releaseId, String stage) {
        try {
            byte[] identity = ("RISK_TEST_RELEASE:" + releaseId + ":" + stage)
                .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity));
        } catch (NoSuchAlgorithmException impossible) {
            throw new ErrorException("缺少 SHA-256 算法", impossible);
        }
    }
}
