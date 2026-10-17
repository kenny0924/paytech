package com.paytech.platform.support.accesscontrol.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Operations 用例输入，不承载 HTTP 请求幂等。 */
@Getter
@AllArgsConstructor
public class ListOperationsRolesRequest {
    /** 可信操作者账号 ID。 */
    @NotBlank
    private final String actor;

    /** 当前会话 ID。 */
    @NotBlank
    private final String session;

    /** 页码，从 1 开始。 */
    @Positive
    private final int page;

    /** 每页条数。 */
    @Positive
    private final int size;

}
