package com.paytech.operations.console.bff.service.model.dataquery.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 从入口键类型出发的可达预览；按键类型推算，不执行查询。 */
@Getter
@Setter
public class JointQueryGraphResponseBody {

    /** 有可达模板的泳道，按 rank 升序；没有时为空列表。 */
    private List<JointQueryGraphGroup> groups;

    /** 链路断点；没有时为空列表。 */
    private List<JointQueryGraphBreak> breaks;

    /** 配置了联合查询却不参与的模板（不限于可达范围）；没有时为空列表。 */
    private List<JointQueryExcludedTemplate> excluded;
}
