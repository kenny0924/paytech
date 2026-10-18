package com.paytech.client.console.bff.service.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 首页的已批准资料摘要。 */
@Getter
@AllArgsConstructor
public class HomeProfile {

    /** 国家或地区代码 */
    private final String country;

    /** 经营模式代码 */
    private final String businessModelCode;

    /** 批准时间，UTC 六位微秒字符串 */
    private final String approvedAt;
}
