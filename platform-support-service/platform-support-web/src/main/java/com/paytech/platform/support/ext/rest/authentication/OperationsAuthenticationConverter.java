package com.paytech.platform.support.ext.rest.authentication;

import org.springframework.stereotype.Component;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.api.authentication.request.ChangePasswordRequestBody;
import com.paytech.platform.support.api.authentication.request.FirstAdministratorRequestBody;
import com.paytech.platform.support.api.authentication.request.OperationsLoginRequestBody;
import com.paytech.platform.support.api.authentication.request.OperationsRefreshRequestBody;
import com.paytech.platform.support.api.authentication.response.AccountCreatedResponseBody;
import com.paytech.platform.support.api.authentication.response.AuthenticationTokensResponseBody;
import com.paytech.platform.support.api.authentication.response.OperationsContextResponseBody;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsContextView;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsAuthenticationModels.TokenResult;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsAuthenticationModels.AccountCreatedResult;
import com.paytech.platform.support.accesscontrol.service.processor.request.FirstOperationsAdministratorRequest;
import com.paytech.platform.support.accesscontrol.service.processor.request.OperationsContextRequest;
import com.paytech.platform.support.accesscontrol.service.processor.request.OperationsLoginRequest;
import com.paytech.platform.support.accesscontrol.service.processor.request.OperationsLogoutRequest;
import com.paytech.platform.support.accesscontrol.service.processor.request.OperationsPasswordRequest;
import com.paytech.platform.support.accesscontrol.service.processor.request.OperationsRefreshRequest;

/** 只负责 Header、Body 和 Owner 模型转换，不读取认证事实。 */
@Component
public class OperationsAuthenticationConverter {
    /** 转换登录字段，资源身份由用例从数据库解析。 */
    public OperationsLoginRequest login(OperationsLoginRequestBody body) {
        return new OperationsLoginRequest(body.getLoginIdentifier(), body.getPassword(), null);
    }

    /** 转换刷新秘密，不能把公开查找段当作认证证明。 */
    public OperationsRefreshRequest refresh(OperationsRefreshRequestBody body) {
        return new OperationsRefreshRequest(body.getRefreshToken(), null);
    }

    /** 解析可信账号和会话 Header。 */
    public OperationsContextRequest context(String accountId, String sessionId) {
        return new OperationsContextRequest(id(accountId), id(sessionId));
    }

    /** 转换退出所需身份与刷新秘密。 */
    public OperationsLogoutRequest logout(String accountId, String sessionId, OperationsRefreshRequestBody body) {
        return new OperationsLogoutRequest(id(accountId), id(sessionId), body.getRefreshToken());
    }

    /** 转换当前身份及新旧密码。 */
    public OperationsPasswordRequest password(String accountId, String sessionId, ChangePasswordRequestBody body) {
        return new OperationsPasswordRequest(id(accountId), id(sessionId), body.getCurrentPassword(), body.getNewPassword());
    }

    /** 转换首次管理员凭据；Bootstrap 资格由用例检查。 */
    public FirstOperationsAdministratorRequest administrator(String accountId, String sessionId, FirstAdministratorRequestBody body) {
        return new FirstOperationsAdministratorRequest(id(accountId), id(sessionId), body.getEmail(), body.getInitialPassword());
    }

    /** 保留已有令牌字段及秒单位期限。 */
    public AuthenticationTokensResponseBody tokens(TokenResult result) {
        if (result == null) { throw new ErrorException("认证事务未返回结果"); }
        return new AuthenticationTokensResponseBody(result.accessToken(), result.tokenType(), result.expiresIn(),
            result.refreshToken(), result.refreshExpiresIn(), result.passwordChangeRequired(), result.bootstrapAdministratorRequired());
    }

    /** 只对外投影查询所需的账号上下文字段。 */
    public OperationsContextResponseBody context(OperationsContextView result) {
        if (result == null) { throw new ErrorException("当前身份事务未返回结果"); }
        return new OperationsContextResponseBody(Long.toString(result.accountId()), Long.toString(result.sessionId()),
            result.accountType().getCode(), result.emailMasked(), result.accountStatus().getCode(),
            result.tenantId() == null ? null : result.tenantId().toString(),
            result.memberId() == null ? null : result.memberId().toString(), result.passwordChangeRequired(),
            result.bootstrapAdministratorRequired(), result.integrationMode());
    }

    /** 返回本次原子创建的账号与成员引用。 */
    public AccountCreatedResponseBody created(AccountCreatedResult result) {
        if (result == null) { throw new ErrorException("首次管理员事务未返回结果"); }
        return new AccountCreatedResponseBody(Long.toString(result.accountId()), Long.toString(result.memberId()), result.version());
    }

    /** 保留原 Header 正整数字符串及 BIGINT 范围合同。 */
    long id(String value) {
        try {
            if (value == null || !value.matches("[1-9][0-9]{0,18}")) { throw new NumberFormatException(); }
            return Long.parseLong(value);
        } catch (NumberFormatException failure) {
            throw new InvalidParameterException("账号或会话标识必须为有效的正整数", failure);
        }
    }
}
