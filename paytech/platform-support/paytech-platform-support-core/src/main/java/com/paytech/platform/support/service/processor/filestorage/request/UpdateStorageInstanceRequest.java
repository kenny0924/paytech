package com.paytech.platform.support.service.processor.filestorage.request;

import com.paytech.platform.support.filedocument.domain.storage.StorageInstanceSettings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按原版本修改存储实例连接设置。 */
@Getter
@AllArgsConstructor
public class UpdateStorageInstanceRequest {

    /** Gateway 传递的可信操作人账号。 */
    @NotBlank(message = "操作人必填")
    private final String actor;

    /** Gateway 传递的可信会话标识。 */
    @NotBlank(message = "会话必填")
    private final String session;

    /** 实例代码。 */
    @NotBlank(message = "实例代码必填")
    private final String instanceCode;

    /** 连接设置；修改时 storageType 为空，沿用原类型。 */
    @NotNull(message = "连接设置必填")
    private final StorageInstanceSettings settings;

    /** 调用方读取的原版本。 */
    @Positive(message = "原版本必须为正整数")
    private final int expectedVersion;

    /** 已去除首尾空白的操作原因。 */
    @NotBlank(message = "原因必填")
    private final String reason;
}
