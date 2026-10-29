package com.paytech.platform.support.api.workflow.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 动作目录选择 DATA_QUERY 查询模板的一项：编码、名称与参数声明，不含 SQL 正文与列声明。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDataQueryTemplateBody {

    /** 模板编码。 */
    private String templateCode;

    /** 模板名称。 */
    private String templateName;

    /** 说明；可空。 */
    private String description;

    /** 当前版本数据源名称；数据源记录缺失时为空。 */
    private String dataSourceName;

    /** 当前版本的参数声明，按声明顺序。 */
    private List<Parameter> parameters;

    /** 当前生效的版本号。 */
    private Integer versionNo;

    /** 模板自身配置的超时秒数（工作流实际取 min(本值, 工作流上限 5 秒, 系统上限 8 秒)）。 */
    private Integer timeoutSeconds;

    /** 一个查询参数。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Parameter {

        /** 参数名。 */
        private String name;

        /** 参数类型，如 STRING、INTEGER、DATE。 */
        private String type;

        /** 是否必须提供（声明必填且无默认值）。 */
        private boolean required;
    }
}
