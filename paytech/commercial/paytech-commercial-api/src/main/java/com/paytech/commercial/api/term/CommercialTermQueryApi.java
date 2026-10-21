package com.paytech.commercial.api.term;

import java.util.List;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.commercial.api.term.response.CommercialTermPlanSummary;
import com.paytech.commercial.api.term.response.CommercialTermTemplateListResponseBody;
import com.paytech.commercial.api.term.response.CommercialTermVersionSummary;
import com.paytech.foundation.web.paging.PagingResponseBody;

/**
 * 商业条款方案与版本的列表查询（运营控制台第二切片 technique.md §2；仅私网，调用方 operations-console-bff）
 * <li>只读，不加锁、不开事务；分页复用公共 page/size（page 默认 1，size 默认 10，只允许 10、20、50、100、200）。</li>
 * <li>审批工作台：status=PENDING_APPROVAL 按提交时刻升序，status=SCHEDULED 按生效起点升序。</li>
 */
@HttpExchange(url = "/commercial-terms/v1", accept = "application/json")
public interface CommercialTermQueryApi {
    /**
     * 标准价目模板方案列表，按 OE、方案编号升序
     *
     * @param entityNo 可选 OE
     * @param termType 可选条款类型
     * @param status 可选方案状态：DRAFT、ACTIVE、RETIRED
     * @return 模板方案，含当前生效版本与最新版本；未知状态 400
     */
    @GetExchange("/templates")
    CommercialTermTemplateListResponseBody listTemplates(
        @RequestParam(value = "entityNo", required = false) String entityNo,
        @RequestParam(value = "termType", required = false) String termType,
        @RequestParam(value = "status", required = false) String status);

    /**
     * 按条件分页查询合同方案（不含标准价目模板），按创建时间、方案 ID 倒序
     *
     * @param agreementId 可选合同 ID，规范正十进制
     * @param clientId 可选 Client ID，规范正十进制
     * @param localPartnerId 可选 Local Partner ID，规范正十进制
     * @param termType 可选条款类型（开放目录代码，如 CLIENT_PRICING）
     * @param status 可选方案状态：DRAFT、ACTIVE、RETIRED
     * @param page 可选页码
     * @param size 可选每页条数
     * @return 方案分页，含当前 ACTIVE 与已排期 SCHEDULED 版本；未知状态或非规范 ID 400
     */
    @GetExchange("/plans")
    PagingResponseBody<CommercialTermPlanSummary> listPlans(
        @RequestParam(value = "agreementId", required = false) String agreementId,
        @RequestParam(value = "clientId", required = false) String clientId,
        @RequestParam(value = "localPartnerId", required = false) String localPartnerId,
        @RequestParam(value = "termType", required = false) String termType,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 按条件分页查询版本
     * <li>排序：status 只有 PENDING_APPROVAL 时按提交时刻升序；只有 SCHEDULED 时按生效起点升序；其他按方案 ID 倒序、版本号倒序。
     * 相同排序值再按版本 ID 升序。</li>
     * <li>effectiveFrom / effectiveTo 表示查询窗口 [effectiveFrom, effectiveTo)，返回生效区间与窗口相交的版本（版本无终点视为无限）；
     * 排期前版本的生效起点是创建时刻占位，排期时改写。</li>
     *
     * @param planId 可选方案 ID，规范正十进制
     * @param agreementId 可选合同 ID，规范正十进制
     * @param ownerType 可选方案 Owner 类型：CLIENT、LOCAL_PARTNER、PLATFORM_TEMPLATE；为空不过滤
     * @param status 可选版本状态，可重复：DRAFT、PENDING_APPROVAL、APPROVED、CLIENT_SIGNED、SCHEDULED、ACTIVE、SUPERSEDED、EXPIRED
     * @param effectiveFrom 可选窗口起点（含），带时区偏移的 ISO 8601
     * @param effectiveTo 可选窗口终点（不含），带时区偏移的 ISO 8601，必须晚于起点
     * @param page 可选页码
     * @param size 可选每页条数
     * @return 版本分页，含 Maker / Checker、提交时刻、价格条数、破标数、签署状态与方案 BCR 根版本；未知状态、非规范 ID 或窗口非法 400
     */
    @GetExchange("/versions")
    PagingResponseBody<CommercialTermVersionSummary> listVersions(
        @RequestParam(value = "planId", required = false) String planId,
        @RequestParam(value = "agreementId", required = false) String agreementId,
        @RequestParam(value = "ownerType", required = false) String ownerType,
        @RequestParam(value = "status", required = false) List<String> status,
        @RequestParam(value = "effectiveFrom", required = false) String effectiveFrom,
        @RequestParam(value = "effectiveTo", required = false) String effectiveTo,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);
}
