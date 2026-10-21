package com.paytech.commercial.api.term.response;

import lombok.Getter;
import lombok.Setter;

/** 标准价目模板方案列表中的一行：方案字段、当前生效版本与最新版本。 */
@Getter
@Setter
public class CommercialTermTemplateSummary {
    /** 方案 ID */
    private String planId;

    /** 方案编号 */
    private String planCode;

    /** 模板显示名 */
    private String templateName;

    /** 条款类型 */
    private String termType;

    /** 模板适用的准确 OE */
    private String entityNo;

    /** 方案状态：DRAFT、ACTIVE、RETIRED */
    private String status;

    /** 方案 BCR 根当前版本，版本写入的 expectedChangeVersion */
    private String changeVersion;

    /** 当前生效版本 ID，没有为空 */
    private String activeVersionId;

    /** 当前生效版本号，没有为空 */
    private String activeVersionNo;

    /** 最新版本 ID（版本号最大），没有为空 */
    private String latestVersionId;

    /** 最新版本号，没有为空 */
    private String latestVersionNo;

    /** 最新版本状态，没有为空 */
    private String latestVersionStatus;

    /** 方案创建时间，UTC ISO 8601 */
    private String createTime;

    /** 方案行最近更新时间，UTC ISO 8601 */
    private String updateTime;
}
