package com.paytech.platform.support.auditsecurity.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.auditsecurity.domain.enums.OperationsAuditClassification;
/** Operations AuditRecord 的本库查询投影。 */
public record OperationsAuditRecord(
    /** 审计事件主键。 */
    long id,
    /** 稳定操作者标识。 */
    String actorId,
    /** 受控动作代码。 */
    String actionCode,
    /** 受控资源类型。 */
    String resourceType,
    /** 稳定资源ID。 */
    String resourceId,
    /** SUCCEEDED/DENIED/FAILED。 */
    String result,
    /** 当前请求标识。 */
    String requestId,
    /** 事件UTC时间。 */
    Instant occurredTime,
    /** 明确 reason 键按分类投影后的安全值，不得含敏感原文。 */
    String reason,
    /** reason 属性的持久化分类；没有 reason 属性时为 null。 */
    OperationsAuditClassification reasonClassification) {
    @Override
    public String toString() {
        return "OperationsAuditRecord[protected projection]";
    }
}
