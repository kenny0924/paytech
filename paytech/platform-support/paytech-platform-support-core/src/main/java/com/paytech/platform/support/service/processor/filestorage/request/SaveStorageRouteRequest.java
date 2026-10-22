package com.paytech.platform.support.service.processor.filestorage.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新建或按原版本修改写入路由。 */
@Getter
@AllArgsConstructor
public class SaveStorageRouteRequest {

    /** Gateway 传递的可信操作人账号。 */
    @NotBlank(message = "操作人必填")
    private final String actor;

    /** Gateway 传递的可信会话标识。 */
    @NotBlank(message = "会话必填")
    private final String session;

    /** 来源系统或 *。 */
    @NotBlank(message = "来源系统必填")
    private final String sourceSystem;

    /** 实例代码。 */
    @NotBlank(message = "实例代码必填")
    private final String instanceCode;

    /** 存储桶名称。 */
    @NotBlank(message = "存储桶名称必填")
    private final String bucketName;

    /** 已去除首尾空白的操作原因。 */
    @NotBlank(message = "原因必填")
    private final String reason;

    /** 扫描策略代码。 */
    @NotBlank(message = "扫描策略必填")
    private final String scanPolicyCode;

    /** 上传授权有效秒数。 */
    @Positive(message = "上传期限必须为正整数")
    private final int maxUploadSeconds;

    /** 修改时调用方读取的原版本；新建为 0。 */
    private final int expectedVersion;
}
