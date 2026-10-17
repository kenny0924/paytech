package com.paytech.platform.support.api.notificationmanagement.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 新建或修改模板某一语言的标题与正文。 */
@Getter
@Setter
public class SaveNotificationContentRequestBody {

    /**
     * 标题模板
     * <li>占位符 ${name}；不可换行，最多 1024 字节，渲染后最多 255 字符。</li>
     */
    @NotNull
    @Size(max = 1024)
    private String subjectTemplate;

    /**
     * 正文模板
     * <li>纯文本，占位符 ${name}，最多 60000 字节；认证邮件模板必须包含 ${link}。</li>
     */
    @NotNull
    @Size(max = 60000)
    private String bodyTemplate;

    /**
     * 当前版本
     * <li>0 表示该语言尚不存在、新建；不一致返回 409。</li>
     */
    @PositiveOrZero
    private int expectedVersion;

    /**
     * 操作原因
     * <li>去除首尾空白后 1..500 字符，写入审计。</li>
     */
    @NotBlank
    @Size(max = 500)
    private String reason;
}
