package com.paytech.platform.support.localization.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 不带资源参数的只读查询请求：语言列表与应用列表。 */
@Getter
@AllArgsConstructor
public class OperatorQuery {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;
}
