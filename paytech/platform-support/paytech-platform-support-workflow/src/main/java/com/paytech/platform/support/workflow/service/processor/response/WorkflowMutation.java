package com.paytech.platform.support.workflow.service.processor.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 写入口的最小内部结果。 */
@Getter
@AllArgsConstructor
public class WorkflowMutation {

    /** 实例对象类型。 */
    public static final String INSTANCE = "WORKFLOW_INSTANCE";

    /** 定义对象类型。 */
    public static final String DEFINITION = "WORKFLOW_DEFINITION";

    /** 模板对象类型。 */
    public static final String TEMPLATE = "WORKFLOW_ACTION_TEMPLATE";

    /** 通知回调对象类型。 */
    public static final String CALLBACK = "WORKFLOW_CALLBACK";

    /** 对象类型。 */
    private final String objectType;

    /** 对象 ID。 */
    private final long objectId;

    /** 保存后的状态 Code。 */
    private final String status;

    /** 保存后的版本；没有时为 null。 */
    private final Integer version;

    /** 发起时是否新建了实例：新建 true，返回同一业务关联键的已有实例 false；其他写入口为 null。 */
    private final Boolean created;

    /**
     * 非发起写入口的结果（created 为 null）。
     *
     * @param objectType 对象类型
     * @param objectId 对象 ID
     * @param status 保存后的状态 Code
     * @param version 保存后的版本
     */
    public WorkflowMutation(String objectType, long objectId, String status, Integer version) {
        this(objectType, objectId, status, version, null);
    }
}
