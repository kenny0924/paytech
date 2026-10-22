package com.paytech.platform.support.workflow.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.workflow.core.enums.WorkflowDefinitionStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** t_workflow_definition 映射对象：流程定义；只在 workflow 模块内使用。 */
@Getter
@Setter
@NoArgsConstructor
public class WorkflowDefinition {

    /** 流程定义的 Snowflake 主键。 */
    private Long definitionId;

    /** 流程定义的业务编码；创建后不可修改，全局唯一。 */
    private String definitionCode;

    /** 流程定义的显示名称，随草稿保存修改。 */
    private String definitionName;

    /** 流程定义的用途说明；未填写时为空。 */
    private String description;

    /** 流程定义是否允许发起新实例；INACTIVE 不影响在途实例。允许值：ACTIVE、INACTIVE。 */
    private WorkflowDefinitionStatus status;

    /** 最新一次发布的版本号，发起实例使用该版本；从未发布时为空。 */
    private Integer currentVersionNo;

    /** 当前唯一草稿的版本号；没有草稿时为空，保证每个定义最多一份草稿。 */
    private Integer draftVersionNo;

    /** 并发更新版本，初始为 1，每次条件更新成功加一。 */
    private Integer version;

    /** 创建流程定义的 Operations 账号 ID（t_user_account.account_id）。 */
    private Long createdBy;

    /** 最后修改流程定义或其草稿的 Operations 账号 ID。 */
    private Long updatedBy;

    /** 该记录在 Platform Support 数据库中的 UTC 微秒创建时间。 */
    private Instant createTime;

    /** 该可变记录最后一次持久化更新的 UTC 微秒时间。 */
    private Instant updateTime;
}
