package com.paytech.platform.support.api.notificationmanagement.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 模板路由。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TemplateRouteItem {

    /** 主账号编码。 */
    private String primaryProviderCode;

    /** 备用账号编码，可空。 */
    private String fallbackProviderCode;

    /** 路由状态：ACTIVE、DISABLED。 */
    private String status;

    /** 路由版本。 */
    private int version;

    /** 更新时间，UTC。 */
    private Instant updateTime;
}
