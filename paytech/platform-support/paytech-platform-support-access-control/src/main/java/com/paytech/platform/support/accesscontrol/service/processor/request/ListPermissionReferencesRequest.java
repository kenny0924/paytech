package com.paytech.platform.support.accesscontrol.service.processor.request;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationCatalog.ReferenceKind;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** ListPermissionReferences 的内部应用请求，字段约束由公共 Processor 触发。 */
@Getter
@AllArgsConstructor
public class ListPermissionReferencesRequest {
    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 不可变权限编码。 */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,127}")
    private final String permissionCode;

    /** 本次唯一的引用类别。 */
    @NotNull
    private final ReferenceKind kind;

    /** 已解析默认值的分页参数，每页最多 100 条。 */
    @NotNull
    private final PagingRequestBody paging;

    /** 目录及引用查询的专项分页上限。 */
    @AssertTrue(message = "每页最多 100 条")
    public boolean isPageSizeValid() {
        return paging == null || paging.size() <= 100;
    }
}
