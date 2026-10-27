package com.paytech.compliance.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次 SCREENING_SEARCH 标准 API 调用的输入。 */
@Getter
@AllArgsConstructor
public class ScreeningSearchCommand {
    /** 服务渠道编码。 */
    private final String channelCode;
    /** 被筛查主体。 */
    private final ScreeningSubject subject;
    /** 筛查类型。 */
    private final String screeningType;
    /** 请求引用（筛查执行 ID）。 */
    private final String clientRef;
}
