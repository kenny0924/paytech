package com.paytech.client.master.core.dal.dataobject;

import com.paytech.client.master.domain.enums.RequirementLevel;
import com.paytech.client.master.domain.enums.SchemaSection;
import com.paytech.client.master.domain.enums.SchemaSubject;
import com.paytech.client.master.domain.enums.WidgetCode;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 采集 Schema 字段（t_onboarding_schema_field），同时是前端渲染模板与提交校验来源。 */
@Getter
@Setter
public class OnboardingSchemaField {
    /** 字段行 ID。 */
    private Long schemaFieldId;
    /** 所属 Schema 版本 ID。 */
    private Long schemaId;
    /** 分区。 */
    private SchemaSection section;
    /** 分区内分组代码。 */
    private String displayGroup;
    /** 字段键。 */
    private String fieldKey;
    /** 控件代码。 */
    private WidgetCode widgetCode;
    /** SELECT 取值目录，可空。 */
    private String catalogCode;
    /** 要求级别。 */
    private RequirementLevel requirementLevel;
    /** 二选一组，可空。 */
    private String requirementGroup;
    /** 适用主体。 */
    private SchemaSubject appliesToSubject;
    /** 格式正则，可空。 */
    private String validationPattern;
    /** 最少字符数，可空。 */
    private Integer minLength;
    /** 最多字符数，可空。 */
    private Integer maxLength;
    /** 可见条件 field_key=value，可空。 */
    private String visibleWhen;
    /** 标题文案键。 */
    private String labelKey;
    /** 帮助文案键，可空。 */
    private String helpKey;
    /** 展示顺序。 */
    private Integer displayOrder;
    /** 创建 UTC 时间。 */
    private Instant createTime;
    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
