package com.paytech.platform.support.referencedata.client;

import com.paytech.platform.support.api.enums.ReferenceDataStatus;

/**
 * 一级行政区（只含后端判定所需字段；名称与本地名归前端展示）。
 *
 * @param code ISO 3166-2 行政区代码，如 AE-DU
 * @param countryCode 所属国家 ISO 3166-1 alpha-2 代码
 * @param parentCode 上级行政区代码；一级行政区为 null
 * @param category ISO 3166-2 行政区类别，如 EMIRATE、REGION（开放集合）
 * @param status Platform 当前状态
 */
public record ReferenceSubdivision(String code, String countryCode, String parentCode, String category,
    ReferenceDataStatus status) implements ReferenceItem {
}
