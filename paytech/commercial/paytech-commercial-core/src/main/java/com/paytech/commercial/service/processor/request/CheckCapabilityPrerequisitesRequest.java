package com.paytech.commercial.service.processor.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 开通项前置核对的内部请求：运营检查、补偿任务、资格事件与重查 Job 共用。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CheckCapabilityPrerequisitesRequest {
    /** 合同 ID */
    @NotNull
    @Positive
    private Long agreementId;
}
