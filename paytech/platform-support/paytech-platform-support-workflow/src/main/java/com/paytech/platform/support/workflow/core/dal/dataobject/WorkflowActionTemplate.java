package com.paytech.platform.support.workflow.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.workflow.core.enums.ActionTemplateStatus;
import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** t_workflow_action_template 映射对象：动作模板；只在 workflow 模块内使用。 */
@Getter
@Setter
@NoArgsConstructor
public class WorkflowActionTemplate {

    /** 动作模板的 Snowflake 主键。 */
    private Long actionTemplateId;

    /** 动作模板的业务编码；创建后不可修改，全局唯一。 */
    private String templateCode;

    /** 动作模板的显示名称。 */
    private String templateName;

    /** 动作的提交与验证方式；DATA_QUERY 本期只预留，不能被发布的流程引用。允许值：FORM、FILE_UPLOAD、PAGE_TASK、MANUAL_CONFIRM、DATA_QUERY。 */
    private WorkflowActionType actionType;

    /** 动作模板的用途说明；未填写时为空。 */
    private String description;

    /** 按动作类型的配置，结构见工作流技术方案 3.3。 */
    private String configJson;

    /** 动作模板是否允许被新发布的流程引用。允许值：ACTIVE、INACTIVE。 */
    private ActionTemplateStatus status;

    /** 并发更新版本，初始为 1，每次修改加一；发布时作为模板版本冻结进版本快照。 */
    private Integer version;

    /** 创建动作模板的 Operations 账号 ID。 */
    private Long createdBy;

    /** 最后修改动作模板的 Operations 账号 ID。 */
    private Long updatedBy;

    /** 该记录在 Platform Support 数据库中的 UTC 微秒创建时间。 */
    private Instant createTime;

    /** 该可变记录最后一次持久化更新的 UTC 微秒时间。 */
    private Instant updateTime;
}
