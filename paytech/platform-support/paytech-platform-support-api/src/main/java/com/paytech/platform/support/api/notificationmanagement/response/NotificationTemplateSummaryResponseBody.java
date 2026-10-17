package com.paytech.platform.support.api.notificationmanagement.response;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 模板列表项。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTemplateSummaryResponseBody {

    /** 模板编码。 */
    private String templateCode;

    /** 介质，本期为 EMAIL。 */
    private String medium;

    /** 模板状态：ACTIVE、DISABLED。 */
    private String status;

    /** 已有语言，升序。 */
    private List<String> locales;

    /**
     * 是否只能即时发送（认证邮件）
     * <li>由部署配置 notification.transient-template-codes 决定。</li>
     */
    private boolean transientTemplate;

    /**
     * 路由状态
     * <li>未配置路由时为空。</li>
     */
    private String routeStatus;

    /** 模板版本。 */
    private int version;

    /** 模板更新时间，UTC。 */
    private Instant updateTime;
}
