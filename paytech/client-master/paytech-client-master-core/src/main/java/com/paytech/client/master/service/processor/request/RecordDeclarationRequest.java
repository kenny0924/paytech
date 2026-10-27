package com.paytech.client.master.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** CM-6 记录声明。 */
@Getter
@AllArgsConstructor
public class RecordDeclarationRequest {
    /** 当前 CLIENT 账号。 */
    @Positive
    private final long accountId;
    /** 当前 CLIENT 会话。 */
    @Positive
    private final long sessionId;
    /** 申请 ID，也是申请资源锁的 Key。 */
    @Positive
    private final long applicationId;
    /** 声明类型。 */
    @NotBlank
    private final String declarationType;
    /** TRUE 或 FALSE。 */
    @NotBlank
    private final String declaredValue;
    /** 条款版本，可空。 */
    private final String termsVersion;
    /** 可信客户端 IP，可空。 */
    private final String clientIp;
    /** User-Agent，可空。 */
    private final String userAgent;
}
