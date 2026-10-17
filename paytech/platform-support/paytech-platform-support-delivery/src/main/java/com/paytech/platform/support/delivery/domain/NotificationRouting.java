package com.paytech.platform.support.delivery.domain;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationProvider;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationRoute;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationTemplate;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 已校验可用的模板、路由与主发送账号；备用账号仅校验，不在此返回。 */
@Getter
@RequiredArgsConstructor
public class NotificationRouting {

    private final NotificationTemplate template;

    private final NotificationRoute route;

    private final NotificationProvider primaryProvider;
}
