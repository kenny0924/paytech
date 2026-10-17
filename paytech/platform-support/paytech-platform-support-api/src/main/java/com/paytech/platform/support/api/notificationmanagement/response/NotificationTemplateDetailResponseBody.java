package com.paytech.platform.support.api.notificationmanagement.response;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 模板详情：模板、全部语言内容与路由。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTemplateDetailResponseBody {

    /** 模板编码。 */
    private String templateCode;

    /** 介质，本期为 EMAIL。 */
    private String medium;

    /** 模板状态：ACTIVE、DISABLED。 */
    private String status;

    /** 是否只能即时发送（认证邮件）。 */
    private boolean transientTemplate;

    /**
     * 正文必须包含的占位符名称
     * <li>即时发送模板为 link，普通模板为空。</li>
     */
    private List<String> requiredParameters;

    /** 模板版本。 */
    private int version;

    /** 模板更新时间，UTC。 */
    private Instant updateTime;

    /** 全部语言内容，按语言升序。 */
    private List<TemplateContentItem> contents;

    /**
     * 路由
     * <li>未配置时为空。</li>
     */
    private TemplateRouteItem route;
}
