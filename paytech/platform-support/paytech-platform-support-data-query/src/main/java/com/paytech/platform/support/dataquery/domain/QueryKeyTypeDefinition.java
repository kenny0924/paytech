package com.paytech.platform.support.dataquery.domain;

import com.paytech.platform.support.dataquery.core.enums.QueryKeyRole;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyValueType;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 键类型可维护的内容：新建与修改共用；编码与状态不在其中。 */
@Getter
@AllArgsConstructor
public final class QueryKeyTypeDefinition {

    /** 显示名称。 */
    private final String keyTypeName;

    /** 业务分组显示名。 */
    private final String groupName;

    /** 值类型。 */
    private final QueryKeyValueType valueType;

    /** 默认角色。 */
    private final QueryKeyRole defaultRole;

    /** 是否可作为入口。 */
    private final boolean entryAllowed;

    /** 入口值校验正则，已通过正则子集检查；可为 null。 */
    private final String valuePattern;

    /** 入口输入提示，可为 null。 */
    private final String inputHint;

    /** 顺序号，0..9999。 */
    private final int sortNo;

    /** 说明，可为 null。 */
    private final String description;
}
