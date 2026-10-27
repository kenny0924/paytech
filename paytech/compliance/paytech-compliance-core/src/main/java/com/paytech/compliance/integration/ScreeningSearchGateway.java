package com.paytech.compliance.integration;

import com.paytech.compliance.domain.ScreeningSearchCommand;
import com.paytech.compliance.domain.ScreeningSearchResult;

/** 经 channel-connector 服务渠道执行 SCREENING_SEARCH 的出站端口；只能在数据库事务外调用。 */
public interface ScreeningSearchGateway {
    /**
     * 执行一次名单筛查
     *
     * @param command 渠道、主体、筛查类型与请求引用
     * @return 规范化结果；请求可能已发出但结果无法确认时为 UNKNOWN，不抛出
     */
    ScreeningSearchResult search(ScreeningSearchCommand command);
}
