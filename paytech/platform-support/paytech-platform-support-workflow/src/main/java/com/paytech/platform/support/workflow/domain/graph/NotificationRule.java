package com.paytech.platform.support.workflow.domain.graph;

import java.util.List;

import com.paytech.platform.support.workflow.core.enums.NotificationChannel;
import com.paytech.platform.support.workflow.core.enums.NotificationRecipient;
import com.paytech.platform.support.workflow.core.enums.NotificationRuleType;
import com.paytech.platform.support.workflow.core.enums.NotificationTrigger;

/**
 * 状态变化时执行的一条规则：通知人员或发布业务回调。
 *
 * @param trigger 触发事件
 * @param type 规则类型
 * @param recipients NOTIFY 的通知对象；CALLBACK 时为空列表
 * @param userIds recipients 含 SPECIFIED_USERS 时的指定用户；否则为空列表
 * @param channels NOTIFY 的通知渠道；CALLBACK 时为空列表
 * @param callbackCode CALLBACK 的回调编码；NOTIFY 时为 null
 */
public record NotificationRule(NotificationTrigger trigger, NotificationRuleType type,
    List<NotificationRecipient> recipients, List<Long> userIds, List<NotificationChannel> channels,
    String callbackCode) {

    /** 复制列表，保持不可变。 */
    public NotificationRule {
        recipients = List.copyOf(recipients);
        userIds = List.copyOf(userIds);
        channels = List.copyOf(channels);
    }
}
