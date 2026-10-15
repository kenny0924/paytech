package com.paytech.platform.support.delivery.domain;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 内容预览：缺少的参数保留原占位符。 */
@Getter
@AllArgsConstructor
public class NotificationPreview {

    /** 渲染后的标题。 */
    private final String subject;

    /** 渲染后的正文。 */
    private final String body;

    /** 未提供值的占位符名称，按出现顺序。 */
    private final List<String> missingParameters;
}
