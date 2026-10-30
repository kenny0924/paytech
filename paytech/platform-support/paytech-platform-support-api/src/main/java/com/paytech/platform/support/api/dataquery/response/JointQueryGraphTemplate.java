package com.paytech.platform.support.api.dataquery.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 可达预览中的一个参与模板。 */
@Getter
@Setter
public class JointQueryGraphTemplate {

    /** 模板编码。 */
    private String templateCode;

    /** 模板名称。 */
    private String templateName;

    /** 当前版本的数据源编码。 */
    private String dataSourceCode;

    /** 行主体键类型编码，决定所在泳道。 */
    private String subjectKeyType;

    /** 触发键类型编码（keyParameter.keyType）。 */
    private String triggerKeyType;

    /** 从入口键类型出发最少需要的层数，入口直接触发为 0；按键类型推算，不代表实际执行的跳数。 */
    private int minSteps;

    /** LINK 列的键类型编码，去重后按出现顺序；没有时为空列表（链路终点）。 */
    private List<String> linkKeyTypes;
}
