package com.paytech.ledger.service.processor.request;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 把一个到期的 APPROVED 规则版本切换为当前生效版本。 */
@Getter
@AllArgsConstructor
public class ActivateRuleVersionRequest {
    /** 规则集代码 */
    @NotBlank
    private final String bundleCode;

    /** 版本号 */
    @NotNull
    private final Long bundleVer;

    /** 判断是否到期的时点（UTC，微秒精度） */
    @NotNull
    private final Instant asOf;
}
