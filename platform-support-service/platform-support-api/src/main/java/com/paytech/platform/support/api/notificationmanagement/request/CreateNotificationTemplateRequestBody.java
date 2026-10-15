package com.paytech.platform.support.api.notificationmanagement.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 新建通知模板；新模板为停用状态、介质固定 EMAIL。 */
@Getter
@Setter
public class CreateNotificationTemplateRequestBody {

    /**
     * 模板编码
     * <li>创建后不可修改。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "编码只能由大写字母开头，包含大写字母、数字或下划线，最多 64 字符")
    private String templateCode;

    /**
     * 操作原因
     * <li>去除首尾空白后 1..500 字符，写入审计。</li>
     */
    @NotBlank
    @Size(max = 500)
    private String reason;
}
