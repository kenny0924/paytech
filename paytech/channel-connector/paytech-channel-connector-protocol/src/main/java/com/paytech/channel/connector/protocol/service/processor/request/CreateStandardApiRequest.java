package com.paytech.channel.connector.protocol.service.processor.request;

import java.util.List;

import com.paytech.channel.connector.protocol.domain.FieldDefinition;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 创建标准 API 的已规范化输入。 */
@Getter
@AllArgsConstructor
public class CreateStandardApiRequest {

    /** 操作者 Account ID（X-Gateway-Actor-Subject，规范正十进制）。 */
    private final long actorId;

    /** 标准 API 编码。 */
    private final String code;

    /** 名称，已去除首尾空白。 */
    private final String name;

    /** 说明，空白已规范为 null。 */
    private final String description;

    /** 请求字段，顺序即显示顺序。 */
    private final List<FieldDefinition> requestFields;

    /** 响应字段，顺序即显示顺序。 */
    private final List<FieldDefinition> responseFields;

    /** 创建原因。 */
    private final String reason;
}
