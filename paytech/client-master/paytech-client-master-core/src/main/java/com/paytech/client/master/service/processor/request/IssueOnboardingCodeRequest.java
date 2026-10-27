package com.paytech.client.master.service.processor.request;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** CM-12 签发入驻码。 */
@Getter
@AllArgsConstructor
public class IssueOnboardingCodeRequest {
    /** Operations 操作者。 */
    @Positive
    private final long actorId;
    /** 签发 Local Partner，也是资源锁的 Key。 */
    @Positive
    private final long localPartnerId;
    /** 生效时间，可空表示即刻。 */
    private final java.time.Instant effectiveFrom;
    /** 失效时间，可空。 */
    private final java.time.Instant effectiveTo;
    /** 最大使用次数，可空。 */
    private final Integer maxUses;
    /** 签发说明，可空。 */
    private final String note;
}
