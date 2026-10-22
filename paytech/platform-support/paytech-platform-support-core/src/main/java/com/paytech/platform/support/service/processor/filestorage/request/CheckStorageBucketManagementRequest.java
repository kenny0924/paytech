package com.paytech.platform.support.service.processor.filestorage.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 检查存储桶并回写结果。 */
@Getter
@AllArgsConstructor
public class CheckStorageBucketManagementRequest {

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
}
