package com.paytech.platform.support.api.workflow.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 动作模板配置的校验结果。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowActionTemplateValidationResponseBody {

    /**
     * 语义问题；没有问题时为空列表，此时保存不会因配置被拒
     * <li>DATA_QUERY 的 data-query 事实问题：itemKey 为参数 key（DATA_QUERY_PARAMETER_*）、输出名（DATA_QUERY_OUTPUT_*，
     * satisfied / missing 为列名），模板不存在或停用时为空；nodeKey、edgeKey 恒为空。</li>
     */
    private List<WorkflowProblemBody> problems;
}
