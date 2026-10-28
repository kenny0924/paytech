package com.paytech.commercial.service.processor.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 定时重查一轮的请求：范围与 XXL-Job 分片。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecheckDueCapabilityPrerequisitesRequest {
    /** 是否包含已开通（ENABLED）的开通项：false 只扫等待前置与前置失效暂停（每 5 分钟），true 全部（每日复核） */
    @NotNull
    private Boolean includeEnabled;

    /** 分片序号，0 起 */
    @NotNull
    @Min(0)
    private Integer shardIndex;

    /** 分片总数，至少 1 */
    @NotNull
    @Min(1)
    private Integer shardTotal;
}
