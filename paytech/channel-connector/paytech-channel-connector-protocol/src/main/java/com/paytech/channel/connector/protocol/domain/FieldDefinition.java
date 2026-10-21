package com.paytech.channel.connector.protocol.domain;

import com.paytech.channel.connector.api.enums.ApiFieldDataType;
import com.paytech.channel.connector.api.enums.ApiFieldLocation;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次保存请求中的字段定义；方向与所属 API 由所在列表和用例决定，顺序即显示顺序。 */
@Getter
@AllArgsConstructor
public class FieldDefinition {

    /** 字段位置。 */
    private final ApiFieldLocation location;

    /** 字段路径，BODY 为点分完整路径，数组段以 [] 结尾。 */
    private final String path;

    /** 显示名称，已去除首尾空白。 */
    private final String name;

    /** 数据类型。 */
    private final ApiFieldDataType dataType;

    /** 是否必填。 */
    private final boolean required;

    /** 字段说明，空白已规范为 null。 */
    private final String description;
}
