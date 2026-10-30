package com.paytech.platform.support.api.dataquery.response;

import lombok.Getter;
import lombok.Setter;

/** 一个联合查询键类型及其引用情况；列表使用。 */
@Getter
@Setter
public class QueryKeyTypeResponseBody {

    /** 键类型 ID，十进制字符串。 */
    private String keyTypeId;

    /** 键类型编码，创建后不可修改。 */
    private String keyTypeCode;

    /** 键类型名称。 */
    private String keyTypeName;

    /** 业务分组显示名；同名即同组。 */
    private String groupName;

    /** 键值类型：INTEGER、STRING。 */
    private String valueType;

    /** 默认角色：LINK、DIMENSION。 */
    private String defaultRole;

    /** 是否出现在联合查询入口下拉。 */
    private boolean entryAllowed;

    /** 入口值校验正则，整串匹配；为 null 不校验格式。 */
    private String valuePattern;

    /** 入口输入提示，可为 null。 */
    private String inputHint;

    /** 业务流程顺序号，升序。 */
    private int sortNo;

    /** 说明，可为 null。 */
    private String description;

    /** 状态：ACTIVE、INACTIVE。 */
    private String status;

    /** 乐观并发版本，写接口按此传 expectedVersion。 */
    private int version;

    /**
     * 引用该键类型的模板数
     * <li>只统计 ACTIVE 模板的当前版本，joint 中 subjectKeyType、keyParameter.keyType 或 columns[].keyType 任一引用即计 1。</li>
     * <li>大于 0 时不能停用，也不能修改 valueType。</li>
     */
    private long referencedTemplateCount;

    /** 最后修改人账号 ID，十进制字符串。 */
    private String updatedBy;

    /** 最后修改时间，UTC ISO-8601 微秒精度。 */
    private String updateTime;
}
