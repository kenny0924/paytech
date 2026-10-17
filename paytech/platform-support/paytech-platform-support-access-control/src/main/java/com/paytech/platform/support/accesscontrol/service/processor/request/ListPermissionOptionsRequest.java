package com.paytech.platform.support.accesscontrol.service.processor.request;

import com.paytech.foundation.web.paging.PagingRequestBody;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** ListPermissionOptions 的内部应用请求，字段约束由公共 Processor 触发。 */
@Getter
@AllArgsConstructor
public class ListPermissionOptionsRequest {
    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 选择器使用场景，决定读取权限。 */
    @NotBlank @Pattern(regexp = "GATEWAY_ROUTE|CONSOLE_RESOURCE")
    private final String usage;

    /** 已解析默认值的分页参数，每页最多 100 条。 */
    @NotNull
    private final PagingRequestBody paging;

    /** 普通文本包含筛选，可空。 */
    @Size(max = 128)
    private final String keyword;

    /** 目录及引用查询的专项分页上限。 */
    @AssertTrue(message = "每页最多 100 条")
    public boolean isPageSizeValid() { return paging == null || paging.size() <= 100; }
}
