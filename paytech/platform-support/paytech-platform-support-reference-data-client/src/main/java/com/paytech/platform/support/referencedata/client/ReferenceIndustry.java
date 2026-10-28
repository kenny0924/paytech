package com.paytech.platform.support.referencedata.client;

import com.paytech.platform.support.api.enums.ReferenceDataStatus;

/**
 * 行业分类（只含后端判定所需字段；名称、描述、图标与排序归前端展示）。
 *
 * @param code 平台行业编码，如 RETAIL_APPAREL
 * @param parentCode 上级大类编码；大类为 null
 * @param level 层级：1 为大类，2 为细类
 * @param defaultMccCode 细类的建议 MCC；大类或未设置时为 null
 * @param status Platform 当前状态
 */
public record ReferenceIndustry(String code, String parentCode, int level, String defaultMccCode,
    ReferenceDataStatus status) implements ReferenceItem {
}
