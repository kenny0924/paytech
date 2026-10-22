package com.paytech.platform.support.service.processor.filestorage.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按原版本删除写入路由。 */
@Getter
@AllArgsConstructor
public class DeleteStorageRouteRequest {

    /** Gateway 传递的可信操作人账号。 */
    @NotBlank(message = "操作人必填")
    private final String actor;

    /** Gateway 传递的可信会话标识。 */
    @NotBlank(message = "会话必填")
    private final String session;

    /** 来源系统或 *。 */
    @NotBlank(message = "来源系统必填")
    private final String sourceSystem;

    /** 调用方读取的原版本。 */
    @Positive(message = "原版本必须为正整数")
    private final int expectedVersion;

    /** 已去除首尾空白的操作原因。 */
    @NotBlank(message = "原因必填")
    private final String reason;
}
