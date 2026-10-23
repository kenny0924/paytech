package com.paytech.platform.support.service.processor.filestorage.request;

import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 登记 DRAFT 存储桶。 */
@Getter
@AllArgsConstructor
public class RegisterStorageBucketRequest {

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

    /** 访问级别；登记后不可修改。 */
    @NotNull(message = "访问级别必填")
    private final FileAccessLevel accessLevel;

    /** 对外基础地址；PUBLIC 必填，PRIVATE 必须为空。 */
    private final String publicBaseUrl;

    /** 已去除首尾空白的操作原因。 */
    @NotBlank(message = "原因必填")
    private final String reason;
}
