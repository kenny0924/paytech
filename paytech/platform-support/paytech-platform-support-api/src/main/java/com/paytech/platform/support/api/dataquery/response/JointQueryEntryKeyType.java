package com.paytech.platform.support.api.dataquery.response;

import lombok.Getter;
import lombok.Setter;

/** 联合查询入口下拉中的一个键类型：ACTIVE 且 entryAllowed。 */
@Getter
@Setter
public class JointQueryEntryKeyType {

    /** 键类型编码。 */
    private String keyTypeCode;

    /** 键类型名称。 */
    private String keyTypeName;

    /** 业务分组显示名。 */
    private String groupName;

    /** 键值类型：INTEGER、STRING。 */
    private String valueType;

    /**
     * 入口值校验正则
     * <li>整串匹配；为 null 不校验格式。只使用 Java 与 JavaScript 行为一致的语法，页面可直接用于即时提示。</li>
     */
    private String valuePattern;

    /** 入口输入提示，可为 null。 */
    private String inputHint;

    /** 业务流程顺序号，下拉按此升序。 */
    private int sortNo;
}
