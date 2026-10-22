package com.paytech.channel.connector.protocol.domain;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 流程编辑器一次取得的标准 API 字段与本渠道全部原子 API 合同。 */
@Getter
@AllArgsConstructor
public class FlowEditorContextView {

    /** 标准 API 请求字段。 */
    private final List<FieldDefinition> requestFields;

    /** 标准 API 响应字段。 */
    private final List<FieldDefinition> responseFields;

    /** 本渠道全部原子 API 合同，按编码排序。 */
    private final List<ApiContract> apis;
}
