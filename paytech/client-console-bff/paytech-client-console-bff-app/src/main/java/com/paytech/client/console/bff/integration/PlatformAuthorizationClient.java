package com.paytech.client.console.bff.integration;

import java.util.List;

import com.paytech.client.console.bff.service.model.ClientActor;

/** Platform CLIENT 实时授权（PLT-I02）的出站端口。 */
public interface PlatformAuthorizationClient {

    /**
     * 判断当前成员被授予了哪些权限。
     *
     * @param actor 当前可信身份
     * @param permissionCodes 待判断的权限码
     * @return Platform 授予的权限码；回执或列表为空时返回空列表
     */
    List<String> grantedPermissions(ClientActor actor, List<String> permissionCodes);
}
