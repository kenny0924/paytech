package com.paytech.platform.support.accesscontrol.service.processor.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Operations 用例输入，不承载 HTTP 请求幂等。 */
@Getter
@AllArgsConstructor
public class AssignOperationsRolesRequest {
    /** 可信操作者账号 ID。 */
    @NotBlank
    private final String actor;

    /** 当前会话 ID。 */
    @NotBlank
    private final String session;

    /** 目标账号。 */
    @Positive
    private final long accountId;

    /** 成员期望版本。 */
    @Positive
    private final int expectedMemberVersion;

    /** 完整所授角色。 */
    @NotEmpty @Size(max = 20)
    private final List<String> roleIds;

    /** 操作原因。 */
    @NotBlank @Size(max = 500)
    private final String reason;

}
