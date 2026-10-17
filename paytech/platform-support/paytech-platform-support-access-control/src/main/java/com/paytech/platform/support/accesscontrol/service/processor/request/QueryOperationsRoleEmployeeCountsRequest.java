package com.paytech.platform.support.accesscontrol.service.processor.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Operations 用例输入，不承载 HTTP 请求幂等。 */
@Getter
@AllArgsConstructor
public class QueryOperationsRoleEmployeeCountsRequest {
    /** 可信操作者账号 ID。 */
    @NotBlank
    private final String actor;

    /** 当前会话 ID。 */
    @NotBlank
    private final String session;

    /** 待查询角色，1 至 200 个且不重复。 */
    @NotEmpty @Size(max = 200)
    private final List<String> roleIds;

}
