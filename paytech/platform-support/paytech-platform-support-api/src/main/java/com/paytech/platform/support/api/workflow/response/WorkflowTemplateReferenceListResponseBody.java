package com.paytech.platform.support.api.workflow.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 动作模板的引用：冻结了该模板的已发布流程版本，按流程编码、版本号倒序。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowTemplateReferenceListResponseBody {

    /** 动作模板 ID。 */
    private String templateId;

    /** 模板当前版本。 */
    private Integer currentTemplateVersion;

    /** 引用。 */
    private List<Item> items;

    /** 一个引用该模板的已发布版本。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {

        /** 流程定义 ID。 */
        private String definitionId;

        /** 流程编码。 */
        private String definitionCode;

        /** 流程名称。 */
        private String definitionName;

        /** 版本号。 */
        private Integer versionNo;

        /** 是否为流程的当前发布版本。 */
        private Boolean currentVersion;

        /** 该版本冻结的模板版本。 */
        private Integer frozenTemplateVersion;

        /** 冻结的模板版本是否等于模板当前版本。 */
        private Boolean sameAsCurrent;

        /** 绑定该版本且运行中的实例数。 */
        private Long runningInstanceCount;
    }
}
