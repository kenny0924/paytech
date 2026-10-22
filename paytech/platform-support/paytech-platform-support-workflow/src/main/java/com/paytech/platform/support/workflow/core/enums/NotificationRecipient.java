package com.paytech.platform.support.workflow.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 通知对象。 */
@Getter
@RequiredArgsConstructor
public enum NotificationRecipient implements CodeEnum {

    /** 本节点动作的执行人。 */
    NODE_EXECUTORS("NODE_EXECUTORS"),

    /** 本节点的审批人。 */
    NODE_APPROVERS("NODE_APPROVERS"),

    /** 实例发起人。 */
    STARTER("STARTER"),

    /** 规则中 userIds 指定的用户。 */
    SPECIFIED_USERS("SPECIFIED_USERS");

    private final String code;
}
