package com.paytech.platform.support.accesscontrol.service.processor.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 资源用例输入，不携带 HTTP 请求幂等身份。 */
@Getter
@AllArgsConstructor
public class UpdateConsoleResourceRequest {
    /** 可信 Operations 操作者。 */
    private final String actor;

    /** 当前会话。 */
    private final String session;

    /** 规范化资源编码。 */
    private final String resourceCode;

    /** 规范化展示名称。 */
    private final String displayName;

    /** 父资源主键，可空。 */
    private final Long parentResourceId;

    /** 组件键，可空；路径由服务端派生。 */
    private final String componentKey;

    /** 可见性权限主键。 */
    private final long permissionId;

    /** 同级排序。 */
    private final int sortOrder;

    /** 调用方期望版本。 */
    private final int expectedVersion;

    /** 规范化操作原因。 */
    private final String reason;

}
