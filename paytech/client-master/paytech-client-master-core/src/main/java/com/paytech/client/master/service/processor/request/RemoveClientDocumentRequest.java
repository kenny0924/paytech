package com.paytech.client.master.service.processor.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 删除材料关联的内部请求；不删除 File 或修改其扫描状态。 */
@Getter
@AllArgsConstructor
public class RemoveClientDocumentRequest {
    /** 当前 CLIENT 账号。 */
    @Positive
    private final long accountId;
    /** 当前 CLIENT 会话，由 Platform 实时核验。 */
    @Positive
    private final long sessionId;
    /** 申请 ID，也是申请资源锁的 Key。 */
    @Positive
    private final long applicationId;
    /** 材料引用 ID。 */
    @Positive
    private final long documentId;
}
