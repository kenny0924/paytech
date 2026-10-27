package com.paytech.client.master.api.onboardingadmin.response;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** CM-10 采集 Schema 版本及字段（全部状态）。 */
@Getter
@Setter
public class AdminOnboardingSchemaResponseBody {
    /** Schema 版本 ID。 */
    private String schemaId;

    /** 辖区代码。 */
    private String jurisdictionCode;

    /** 主体类型。 */
    private String partyType;

    /** 角色。 */
    private String roleType;

    /** 组织类型；* 为默认。 */
    private String organizationTypeCode;

    /** 版本号。 */
    private int versionNo;

    /** 状态 DRAFT、ACTIVE、RETIRED。 */
    private String status;

    /** 发布 UTC 时间，可空。 */
    private String approvedTime;

    /** 字段，按展示顺序。 */
    private List<SchemaFieldItem> fields;

    /** 一个字段定义。 */
    @Getter
    @Setter
    public static class SchemaFieldItem {
        /** 分区。 */
        private String section;

        /** 分组。 */
        private String displayGroup;

        /** 字段键。 */
        private String fieldKey;

        /** 控件代码。 */
        private String widgetCode;

        /** 取值目录，可空。 */
        private String catalogCode;

        /** 要求级别。 */
        private String requirementLevel;

        /** 二选一组，可空。 */
        private String requirementGroup;

        /** 适用主体。 */
        private String appliesToSubject;

        /** 格式正则，可空。 */
        private String validationPattern;

        /** 最少字符数，可空。 */
        private Integer minLength;

        /** 最多字符数，可空。 */
        private Integer maxLength;

        /** 可见条件，可空。 */
        private String visibleWhen;

        /** 标题文案键。 */
        private String labelKey;

        /** 帮助文案键，可空。 */
        private String helpKey;

        /** 展示顺序。 */
        private int displayOrder;
    }
}
