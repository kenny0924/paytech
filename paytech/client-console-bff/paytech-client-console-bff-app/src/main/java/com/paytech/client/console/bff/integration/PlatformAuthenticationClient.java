package com.paytech.client.console.bff.integration;

import java.util.List;

import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.client.console.bff.service.model.SessionGrant;
import com.paytech.platform.support.api.authentication.request.LoginRequestBody;
import com.paytech.platform.support.api.authentication.response.AccountContextResponseBody;
import com.paytech.platform.support.api.authentication.response.ClientWorkspaceResponseBody;

/** Platform CLIENT 会话与工作区成员的出站端口；会话事实与撤销规则只属于 Platform。 */
public interface PlatformAuthenticationClient {

    /**
     * 密码登录。
     *
     * @param body 浏览器登录请求，已通过入口校验
     * @return 已校验的会话凭据
     */
    SessionGrant login(LoginRequestBody body);

    /**
     * 用 Refresh Token 轮换会话。
     *
     * @param refreshToken 来自唯一 host Cookie
     * @return 已校验的新会话凭据
     */
    SessionGrant refresh(String refreshToken);

    /**
     * 退出当前会话；Platform 判定会话已失效时视为已经退出。
     *
     * @param actor 当前可信身份
     * @param refreshToken 来自唯一 host Cookie，由 Platform 确认与 Access 属于同一会话
     */
    void logout(ClientActor actor, String refreshToken);

    /**
     * 切换到成员所在工作区（C02），新建会话。
     *
     * @param actor 当前可信身份
     * @param refreshToken 来自唯一 host Cookie
     * @param memberId 浏览器提交的成员 ID，归属与状态由 Platform 判定
     * @return 已校验的新会话凭据
     */
    SessionGrant selectWorkspace(ClientActor actor, String refreshToken, String memberId);

    /**
     * 切回账号上下文（W01），新建会话。
     *
     * @param actor 当前可信身份
     * @param refreshToken 来自唯一 host Cookie
     * @return 已校验的新会话凭据
     */
    SessionGrant selectAccountContext(ClientActor actor, String refreshToken);

    /** 实时回读当前会话上下文，不从 JWT 推导账号可操作范围。 */
    AccountContextResponseBody context(ClientActor actor);

    /**
     * 当前账号的工作区成员列表。
     *
     * @param actor 当前可信身份
     * @return 数量、ID 格式、可访问标志与原因码自洽的成员列表；否则按依赖不可用抛出
     */
    List<ClientWorkspaceResponseBody> workspaces(ClientActor actor);
}
