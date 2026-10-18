package com.paytech.platform.support.api.notificationmanagement.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 内容预览结果。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreviewResponseBody {

    /** 渲染后的标题。 */
    private String subject;

    /** 渲染后的正文。 */
    private String body;

    /** 未提供值的占位符，按出现顺序；这些占位符在结果中原样保留。 */
    private List<String> missingParameters;
}
