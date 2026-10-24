package com.paytech.platform.support.service.processor.files.request;

import com.paytech.platform.support.filedocument.core.enums.FileAccessLevel;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 查询调用方开始上传时生效的写入限制；来源系统取内部调用身份。 */
@Getter
@AllArgsConstructor
public class QueryFileUploadConstraintsRequest {

    /** 来源系统，即内部调用身份的服务名。 */
    @NotNull(message = "来源系统必填")
    @Pattern(regexp = "[a-z][a-z0-9-]{0,63}", message = "来源系统必须为服务名")
    private final String sourceSystem;

    /** 访问级别；入口已把缺省解析为 PRIVATE。 */
    @NotNull(message = "访问级别必填")
    private final FileAccessLevel accessLevel;
}
