package com.paytech.client.master.service.processor.response;

/** 资格变化的落地结论；都应确认消息。 */
public enum ApplyQualificationChangeResponse {
    /** 本次挂起了 Client。 */
    SUSPENDED,

    /** 与 Client 挂起无关（非 CLIENT 角色、非 REVOKED 或 Client 已非 ACTIVE），忽略。 */
    IGNORED
}
