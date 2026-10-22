package com.paytech.platform.support.workflow.core.common;

/** 工作流 7 项 Operations 权限编码（技术方案第 7 节，注册数据块 127）。 */
public final class WorkflowPermissions {

    /** 查看流程定义与动作模板。 */
    public static final String DEFINITION_READ = "WORKFLOW_DEFINITION_READ";

    /** 维护流程定义。 */
    public static final String DEFINITION_MANAGE = "WORKFLOW_DEFINITION_MANAGE";

    /** 维护动作目录。 */
    public static final String ACTION_TEMPLATE_MANAGE = "WORKFLOW_ACTION_TEMPLATE_MANAGE";

    /** 发起流程。 */
    public static final String INSTANCE_START = "WORKFLOW_INSTANCE_START";

    /** 参与流程：待办、执行、审批、查看参与的实例。 */
    public static final String TASK_HANDLE = "WORKFLOW_TASK_HANDLE";

    /** 查看全部实例。 */
    public static final String INSTANCE_READ = "WORKFLOW_INSTANCE_READ";

    /** 终止或转交任意实例、手动重试通知回调。 */
    public static final String INSTANCE_MANAGE = "WORKFLOW_INSTANCE_MANAGE";

    private WorkflowPermissions() {
    }
}
