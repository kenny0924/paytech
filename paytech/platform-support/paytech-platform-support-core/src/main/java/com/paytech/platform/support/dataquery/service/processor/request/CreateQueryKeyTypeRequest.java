package com.paytech.platform.support.dataquery.service.processor.request;

import com.paytech.platform.support.dataquery.domain.QueryKeyTypeDefinition;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新建键类型的内部请求。 */
@Getter
@AllArgsConstructor
public class CreateQueryKeyTypeRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 编码。 */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}")
    private final String keyTypeCode;

    /** 内容，文本已去除首尾空白。 */
    @NotNull
    private final QueryKeyTypeDefinition definition;
}
