package com.paytech.platform.support.workflow.domain.query;

import lombok.Getter;
import lombok.Setter;

/** 冻结了某动作模板的已发布流程版本。 */
@Getter
@Setter
public class WorkflowTemplateReferenceView {

    /** 流程版本 ID。 */
    private Long definitionVersionId;

    /** 流程定义 ID。 */
    private Long definitionId;

    /** 流程编码。 */
    private String definitionCode;

    /** 流程名称。 */
    private String definitionName;

    /** 版本号。 */
    private Integer versionNo;

    /** 是否为流程的当前发布版本。 */
    private Boolean currentVersion;

    /** 版本的动作模板快照，供调用方精确核对与读取冻结版本。 */
    private String actionSnapshotJson;

    /** 绑定该版本且运行中的实例数。 */
    private Long runningInstanceCount;
}
