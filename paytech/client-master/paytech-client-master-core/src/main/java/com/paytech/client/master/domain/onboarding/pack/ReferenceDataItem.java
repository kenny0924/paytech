package com.paytech.client.master.domain.onboarding.pack;

/**
 * Platform 参考数据中的一条编码在本服务的最小视图：只保留校验需要的事实，不缓存
 *
 * @param code 编码（国家、币种、行政区、行业或 MCC）
 * @param active Platform 状态是否为 ACTIVE；其他状态或未知状态均为 false（失败关闭）
 * @param countryCode 行政区所属国家；其他类型为 null
 * @param level 行业层级（1 大类、2 细类）；其他类型为 null
 */
public record ReferenceDataItem(String code, boolean active, String countryCode, Integer level) {
}
