package com.paytech.platform.support.delivery.domain;

import java.util.List;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationRequest;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationDelivery;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 完整原子受理上下文，不保留邮箱原文或渲染正文。 */
@Getter
@RequiredArgsConstructor
public class SubmitNotificationContext {

    private final NotificationRequest request;

    private final List<NotificationDelivery> deliveries;
}
