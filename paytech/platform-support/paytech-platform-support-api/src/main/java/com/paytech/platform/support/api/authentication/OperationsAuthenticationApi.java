package com.paytech.platform.support.api.authentication;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.platform.support.api.authentication.request.ChangePasswordRequestBody;
import com.paytech.platform.support.api.authentication.request.FirstAdministratorRequestBody;
import com.paytech.platform.support.api.authentication.request.OperationsLoginRequestBody;
import com.paytech.platform.support.api.authentication.request.OperationsRefreshRequestBody;
import com.paytech.platform.support.api.authentication.response.AccountCreatedResponseBody;
import com.paytech.platform.support.api.authentication.response.AuthenticationTokensResponseBody;
import com.paytech.platform.support.api.authentication.response.OperationsContextResponseBody;

import jakarta.validation.Valid;

/** Operations 专属内部认证契约；仅由部署私网中的 Operations BFF 调用。 */
@HttpExchange("/authentication/v1/operations")
public interface OperationsAuthenticationApi {

    /**
     * 登录并建立固定 Operations 上下文；凭据与结果不得记录 Body。
     *
     * @param request
     *            登录标识与密码；禁止记录请求体。
     * @return 账号会话的 Access/Refresh 与受限操作提示。
     */
    @PostExchange("/login")
    AuthenticationTokensResponseBody login(@Valid @RequestBody OperationsLoginRequestBody request);

    /**
     * 单次消费 Refresh Generation；正确旧代重用撤销整个 Session。
     *
     * @param request
     *            当前刷新令牌；正确旧代重用会撤销会话。
     * @return 轮换后的 Access/Refresh。
     */
    @PostExchange("/refresh")
    AuthenticationTokensResponseBody refresh(@Valid @RequestBody OperationsRefreshRequestBody request);

    /**
     * 撤销当前 Session；调用方随后清除本地持有的 Token。
     *
     * @param accountId
     *            可信账号 Header。
     * @param sessionId
     *            可信会话 Header。
     * @param request
     *            同一会话的刷新秘密。
     */
    @PostExchange("/logout")
    void logout(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @Valid @RequestBody OperationsRefreshRequestBody request);

    /**
     * 查询当前事实；不把 Token 中的 Role/Permission 作为权威。
     *
     * @param accountId
     *            可信账号 Header。
     * @param sessionId
     *            可信会话 Header。
     * @return 根据当前本库事实形成的账号上下文。
     */
    @GetExchange("/context")
    OperationsContextResponseBody context(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId);

    /**
     * 修改本人密码并撤销当前及其余 Session；成功后重新登录。
     *
     * @param accountId
     *            可信账号 Header。
     * @param sessionId
     *            可信会话 Header。
     * @param request
     *            当前密码与新密码；成功后须重新登录。
     */
    @PostExchange("/password")
    void changePassword(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @Valid @RequestBody ChangePasswordRequestBody request);

    /**
     * Bootstrap 改密后创建首个 Email Admin；同时永久关闭 Bootstrap 登录能力。
     *
     * @param accountId
     *            Bootstrap 账号 Header。
     * @param sessionId
     *            Bootstrap 当前会话 Header。
     * @param request
     *            首个管理员邮箱和初始密码。
     * @return 新建账号、成员及初始版本。
     */
    @PostExchange("/bootstrap/administrator")
    AccountCreatedResponseBody createFirstAdministrator(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @Valid @RequestBody FirstAdministratorRequestBody request);
}
