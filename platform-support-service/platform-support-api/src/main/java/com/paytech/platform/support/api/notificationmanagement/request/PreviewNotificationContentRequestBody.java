package com.paytech.platform.support.api.notificationmanagement.request;

import java.util.Map;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 渲染内容草稿，不发送、不保存。 */
@Getter
@Setter
public class PreviewNotificationContentRequestBody {

    /** 标题模板草稿。 */
    @NotNull
    @Size(max = 1024)
    private String subjectTemplate;

    /** 正文模板草稿。 */
    @NotNull
    @Size(max = 60000)
    private String bodyTemplate;

    /**
     * 示例参数
     * <li>可空，最多 64 项，值最多 2048 字符；缺少的占位符原样保留。</li>
     */
    @Size(max = 64)
    private Map<@Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}") String, @NotNull @Size(max = 2048) String> parameters;
}
