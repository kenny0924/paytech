package com.paytech.platform.support.service.processor.filestorage.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按原版本废弃草稿、激活草稿或停用 ACTIVE 修订。 */
@Getter
@AllArgsConstructor
public class ChangeScanPolicyRevisionRequest {

    /** Gateway 传递的可信操作人账号。 */
    @NotBlank(message = "操作人必填")
    private final String actor;

    /** Gateway 传递的可信会话标识。 */
    @NotBlank(message = "会话必填")
    private final String session;

    /** 策略代码。 */
    @NotBlank(message = "策略代码必填")
    private final String policyCode;

    /** 目标修订号；停用作用于 ACTIVE 修订，为空。 */
    @Positive(message = "修订号必须为正整数")
    private final Integer revisionNo;

    /** 调用方读取的目标修订原版本。 */
    @Positive(message = "原版本必须为正整数")
    private final int expectedVersion;

    /** 已去除首尾空白的操作原因。 */
    @NotBlank(message = "原因必填")
    private final String reason;
}
