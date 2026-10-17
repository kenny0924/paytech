package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.risklist.AddRiskListEntryRequestBody;
import com.paytech.operations.console.bff.service.model.risklist.CreateRiskListRequestBody;
import com.paytech.operations.console.bff.service.model.risklist.DeactivateRiskListEntryRequestBody;
import com.paytech.operations.console.bff.service.model.risklist.RiskListEntryResponseBody;
import com.paytech.operations.console.bff.service.model.risklist.RiskListResponseBody;

/** Risk 名单维护的出站端口；主体原值只经过请求，不进入日志或响应。 */
public interface RiskListClient {
    /** 新建名单。 */
    RiskListResponseBody createList(OperatorContext context, CreateRiskListRequestBody body);

    /** 按编码升序分页查询名单。 */
    PagingResponseBody<RiskListResponseBody> listLists(OperatorContext context, int page, int size);

    /** 向名单加入成员。 */
    RiskListEntryResponseBody addEntry(OperatorContext context, String listCode, AddRiskListEntryRequestBody body);

    /** 失效名单成员。 */
    RiskListEntryResponseBody deactivateEntry(OperatorContext context, String listCode, String entryId,
        DeactivateRiskListEntryRequestBody body);

    /** 按主体原值（精确）或状态分页查询成员；响应只含脱敏键。 */
    PagingResponseBody<RiskListEntryResponseBody> listEntries(OperatorContext context, String listCode,
        String subjectValue, String status, int page, int size);
}
