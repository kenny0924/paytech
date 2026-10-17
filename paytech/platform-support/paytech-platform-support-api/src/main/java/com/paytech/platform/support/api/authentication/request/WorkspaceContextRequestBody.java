package com.paytech.platform.support.api.authentication.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Client 私网进入工作区请求：Cookie 证明加浏览器选择的成员编号。 */
public final class WorkspaceContextRequestBody {
    /** BFF 从 HttpOnly Cookie 取出的刷新令牌；仅私网传递。 */
    @NotBlank @Size(max = 66)
    private final String refreshToken;

    /** 浏览器选择的成员编号；归属与状态由 Platform 核对，不能据此指定租户。 */
    @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}")
    private final String memberId;

    /** 绑定协议字段，不输出任何凭据。 */
    @JsonCreator
    public WorkspaceContextRequestBody(@JsonProperty("refreshToken") String refreshToken,
        @JsonProperty("memberId") String memberId) {
        this.refreshToken = refreshToken;
        this.memberId = memberId;
    }

    /** 返回 BFF 从 HttpOnly Cookie 取出的刷新令牌。 */
    public String getRefreshToken() { return refreshToken; }

    /** 返回浏览器选择的成员编号。 */
    public String getMemberId() { return memberId; }

    /** 避免诊断输出泄露认证材料。 */
    @Override
    public String toString() { return "WorkspaceContextRequestBody[REDACTED]"; }
}
