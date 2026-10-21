package com.paytech.channel.connector.protocol.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 引用原子 API 的标准 API 实现及其节点。 */
@Getter
@AllArgsConstructor
public class ApiReference {

    /** 标准 API 编码。 */
    private final String standardApiCode;

    /** 流程中引用该 API 的节点标识。 */
    private final java.util.List<String> nodeIds;
}
