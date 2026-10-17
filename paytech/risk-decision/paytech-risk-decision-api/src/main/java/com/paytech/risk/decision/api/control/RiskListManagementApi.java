package com.paytech.risk.api.control;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.risk.api.control.request.AddRiskListEntryRequestBody;
import com.paytech.risk.api.control.request.CreateRiskListRequestBody;
import com.paytech.risk.api.control.request.DeactivateRiskListEntryRequestBody;
import com.paytech.risk.api.control.response.RiskListEntryResponseBody;
import com.paytech.risk.api.control.response.RiskListResponseBody;

import jakarta.validation.Valid;

/**
 * 运营名单维护的内部 HTTP 合同；一期无页面，由内部调用维护。
 * HTTP 请求幂等由 Gateway 负责；名单编码与同主体生效成员唯一性由 Risk 保证。
 */
@HttpExchange("/risk-lists/v1")
public interface RiskListManagementApi {

    /**
     * 创建名单
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID
     * @param sessionId Gateway 传入的可信会话标识
     * @param requestBody 名单编码、名称、主体类型与用途
     * @return 新名单；编码已存在返回 400，并发创建同编码返回 409 CONCURRENT_REQUEST
     */
    @PostExchange("/lists")
    RiskListResponseBody createList(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @Valid @RequestBody CreateRiskListRequestBody requestBody);

    /**
     * 按编码分页查询名单
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID
     * @param sessionId Gateway 传入的可信会话标识
     * @param page 页码，缺省为 1
     * @param size 每页条数，缺省为 10，只允许 10、20、50、100、200
     * @return 按编码升序的名单分页
     */
    @GetExchange("/lists")
    PagingResponseBody<RiskListResponseBody> listLists(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 人工添加名单成员
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID，记录为添加人
     * @param sessionId Gateway 传入的可信会话标识
     * @param listCode 名单编码
     * @param requestBody 主体原值、生效区间与原因
     * @return 新成员，主体键脱敏；名单不存在或停用、格式不合法返回 400；同主体已有生效成员返回 409
     */
    @PostExchange("/lists/{listCode}/entries")
    RiskListEntryResponseBody addEntry(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("listCode") String listCode,
        @Valid @RequestBody AddRiskListEntryRequestBody requestBody);

    /**
     * 人工失效名单成员
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID，记录为失效人
     * @param sessionId Gateway 传入的可信会话标识
     * @param listCode 名单编码
     * @param entryId 成员 ID，正整数十进制字符串
     * @param requestBody 失效原因
     * @return 已失效成员；成员不存在、不属于该名单或已失效返回 400
     */
    @PostExchange("/lists/{listCode}/entries/{entryId}/deactivate")
    RiskListEntryResponseBody deactivateEntry(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("listCode") String listCode,
        @PathVariable("entryId") String entryId,
        @Valid @RequestBody DeactivateRiskListEntryRequestBody requestBody);

    /**
     * 分页查询名单成员
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID
     * @param sessionId Gateway 传入的可信会话标识
     * @param listCode 名单编码
     * @param subjectValue 可选主体原值，按规范化后的键精确查询
     * @param status 可选状态 Code
     * @param page 页码，缺省为 1
     * @param size 每页条数，缺省为 10
     * @return 新成员在前的分页，主体键脱敏
     */
    @GetExchange("/lists/{listCode}/entries")
    PagingResponseBody<RiskListEntryResponseBody> listEntries(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("listCode") String listCode,
        @RequestParam(value = "subjectValue", required = false) String subjectValue,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);
}
