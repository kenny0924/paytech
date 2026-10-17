package com.paytech.client.console.bff.service.model;
import com.paytech.platform.support.api.authentication.response.AccountContextResponseBody;
/** 浏览器认证投影有意不包含 Refresh Secret。 */
public final class ClientBrowserModels {
    private ClientBrowserModels() {}
    /** Access Token 仅在页面内存保存，时间为 UTC 六位微秒字符串。 */
    public record TokenGrantResponseBody(AccountContextResponseBody context,String accessToken,String expiresAt) {
        @Override public String toString() { return "TokenGrantResponseBody[REDACTED]"; }
    }
    /** C02 进入工作区：只提交成员 ID，Tenant 由 Platform 从真实成员解析。 */
    public record WorkspaceSelectRequestBody(
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Pattern(regexp="[1-9][0-9]{0,18}") String memberId) {}
}
