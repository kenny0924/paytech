package com.paytech.client.console.bff.service.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 首页的当前工作区身份；CCT 未返回已批准资料时只有 clientId。 */
@Getter
@AllArgsConstructor
public class HomeWorkspace {

    /** Client ID */
    private final String clientId;

    /** 客户编号 */
    private final String clientNo;

    /** 显示名称 */
    private final String displayName;

    /** 客户类型 */
    private final String clientType;

    /** 客户状态 */
    private final String clientStatus;
}
