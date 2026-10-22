package com.paytech.platform.support.filedocument.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 对已登记的存储桶执行一次启用检查。 */
@Getter
@AllArgsConstructor
public class CheckStorageBucketRequest {

    /** 实例代码。 */
    @NotBlank(message = "实例代码必填")
    private final String instanceCode;

    /** 存储桶名称。 */
    @NotBlank(message = "存储桶名称必填")
    private final String bucketName;
}
