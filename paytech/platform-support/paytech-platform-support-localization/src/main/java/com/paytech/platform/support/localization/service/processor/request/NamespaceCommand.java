package com.paytech.platform.support.localization.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新增、修改或删除命名空间的内部请求；新增时 expectedVersion 为 null，删除时只用编码与版本。 */
@Getter
@AllArgsConstructor
public class NamespaceCommand {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 应用编码。 */
    @NotBlank
    private final String applicationCode;

    /** 命名空间编码。 */
    @NotBlank
    @Size(max = 64)
    private final String namespaceCode;

    /** 调用方读取的原版本；新增时为 null。 */
    private final Integer expectedVersion;

    /** 命名空间名称；删除时为 null。 */
    @Size(max = 64)
    private final String namespaceName;

    /** 说明，可为 null。 */
    @Size(max = 512)
    private final String description;

    /** 排序值；删除时为 0。 */
    private final int sortOrder;
}
