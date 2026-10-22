package com.paytech.platform.support.service.processor.filestorage.request;

import com.paytech.platform.support.api.enums.FileStorageBucketStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按原版本切换存储桶状态。 */
@Getter
@AllArgsConstructor
public class ChangeStorageBucketStatusRequest {

    /** Gateway 传递的可信操作人账号。 */
    @NotBlank(message = "操作人必填")
    private final String actor;

    /** Gateway 传递的可信会话标识。 */
    @NotBlank(message = "会话必填")
    private final String session;

    /** 实例代码。 */
    @NotBlank(message = "实例代码必填")
    private final String instanceCode;

    /** 存储桶名称。 */
    @NotBlank(message = "存储桶名称必填")
    private final String bucketName;

    /** 调用方读取的原版本。 */
    @Positive(message = "原版本必须为正整数")
    private final int expectedVersion;

    /** 已去除首尾空白的操作原因。 */
    @NotBlank(message = "原因必填")
    private final String reason;

    /** 目标状态。 */
    @NotNull(message = "目标状态必须为 ACTIVE、READ_ONLY 或 DISABLED")
    private final FileStorageBucketStatus status;
}
