package com.paytech.client.master.service.processor.request;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** CM-12 作废入驻码。 */
@Getter
@AllArgsConstructor
public class RevokeOnboardingCodeRequest {
    /** Operations 操作者。 */
    @Positive
    private final long actorId;
    /** Local Partner，也是资源锁的 Key。 */
    @Positive
    private final long localPartnerId;
    /** 入驻码 ID。 */
    @Positive
    private final long codeId;
}
