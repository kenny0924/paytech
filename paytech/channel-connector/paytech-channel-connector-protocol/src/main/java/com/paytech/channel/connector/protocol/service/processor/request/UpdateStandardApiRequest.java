package com.paytech.channel.connector.protocol.service.processor.request;

import java.util.List;

import com.paytech.channel.connector.protocol.domain.FieldDefinition;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 修改标准 API 的已规范化输入。 */
@Getter
@AllArgsConstructor
public class UpdateStandardApiRequest {

    /** 操作者 Account ID（X-Gateway-Actor-Subject，规范正十进制）。 */
    private final long actorId;

    /** 路径中的标准 API 编码。 */
    private final String code;

    /** 新名称。 */
    private final String name;

    /** 新说明，null 表示清空。 */
    private final String description;

    /** 新请求字段。 */
    private final List<FieldDefinition> requestFields;

    /** 新响应字段。 */
    private final List<FieldDefinition> responseFields;

    /** 调用方读取的版本。 */
    private final int expectedVersion;

    /** 修改原因。 */
    private final String reason;
}
