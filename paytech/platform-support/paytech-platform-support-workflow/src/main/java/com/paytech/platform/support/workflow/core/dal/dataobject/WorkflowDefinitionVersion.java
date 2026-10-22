package com.paytech.platform.support.workflow.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.workflow.core.enums.WorkflowVersionStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** t_workflow_definition_version 映射对象：流程版本；只在 workflow 模块内使用。 */
@Getter
@Setter
@NoArgsConstructor
public class WorkflowDefinitionVersion {

    /** 流程版本的 Snowflake 主键。 */
    private Long definitionVersionId;

    /** 该版本所属的流程定义 ID。 */
    private Long definitionId;

    /** 定义内从 1 开始递增的版本号；草稿发布后号码不变。 */
    private Integer versionNo;

    /** DRAFT 可随草稿保存覆盖；PUBLISHED 后内容不可变。允许值：DRAFT、PUBLISHED。 */
    private WorkflowVersionStatus status;

    /** 节点、连线、排他条件、审批、动作引用与通知规则，结构见工作流技术方案 3.1；不超过 48 KiB。 */
    private String graphJson;

    /** 启动表单字段定义，与表单动作共用结构，见工作流技术方案 3.2；最多 50 个字段，不超过 12 KiB。 */
    private String startFormJson;

    /** 发布时冻结的引用动作模板：模板 ID、模板版本、类型与配置；草稿为空。 */
    private String actionSnapshotJson;

    /** 发布该版本的 Operations 账号 ID；草稿为空。 */
    private Long publishedBy;

    /** 版本发布的 UTC 微秒时间；草稿为空。 */
    private Instant publishedTime;

    /** 并发更新版本，初始为 1，每次条件更新成功加一。 */
    private Integer version;

    /** 创建该版本（新建定义或从发布版本复制草稿）的 Operations 账号 ID。 */
    private Long createdBy;

    /** 最后保存该草稿或发布该版本的 Operations 账号 ID。 */
    private Long updatedBy;

    /** 该记录在 Platform Support 数据库中的 UTC 微秒创建时间。 */
    private Instant createTime;

    /** 该可变记录最后一次持久化更新的 UTC 微秒时间。 */
    private Instant updateTime;
}
