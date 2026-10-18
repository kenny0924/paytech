package com.paytech.platform.support.api.notificationmanagement.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 引用发送账号的路由。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProviderReferenceItem {

    /** 路由所属模板编码。 */
    private String templateCode;

    /** PRIMARY 或 FALLBACK。 */
    private String role;

    /** 路由状态：ACTIVE、DISABLED。 */
    private String routeStatus;
}
