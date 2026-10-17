package com.paytech.platform.support.accesscontrol.service.processor.request;

import java.util.List;

import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsAuthenticationModels.PreparedHumanCredentials;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Operations 用例输入，不承载 HTTP 请求幂等。 */
@Getter
@AllArgsConstructor
public class CreateOperationsAccountRequest {
    /** 可信操作者账号 ID。 */
    @NotBlank
    private final String actor;

    /** 当前会话 ID。 */
    @NotBlank
    private final String session;

    /** 登录邮箱，不得输出日志。 */
    @NotBlank @Size(max = 320)
    private final String email;

    /** 初始密码，不得输出日志。 */
    @NotBlank @Size(max = 512)
    private final String initialPassword;

    /** 所授角色 ID。 */
    @NotEmpty @Size(max = 20)
    private final List<String> roleIds;

    /** 操作原因。 */
    @NotBlank @Size(max = 500)
    private final String reason;

    /** 仅由 Processor 在事务外补齐；HTTP Converter 不接受此值。 */
    private final PreparedHumanCredentials prepared;

    /** HTTP 与模块调用仅提供业务输入。 */
    public CreateOperationsAccountRequest(String actor, String session, String email, String initialPassword,
        List<String> roleIds, String reason) {
        this(actor, session, email, initialPassword, roleIds, reason, null);
    }
}
