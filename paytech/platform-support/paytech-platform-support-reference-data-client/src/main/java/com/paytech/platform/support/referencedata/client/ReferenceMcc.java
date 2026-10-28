package com.paytech.platform.support.referencedata.client;

import com.paytech.platform.support.api.enums.ReferenceDataStatus;

/**
 * MCC（只含后端判定所需字段；名称与描述归前端展示）。
 *
 * @param code 四位 Merchant Category Code，保留前导零
 * @param networkHighRisk 卡组织是否公布为高风险类目
 * @param status Platform 当前状态
 */
public record ReferenceMcc(String code, boolean networkHighRisk, ReferenceDataStatus status) implements ReferenceItem {
}
