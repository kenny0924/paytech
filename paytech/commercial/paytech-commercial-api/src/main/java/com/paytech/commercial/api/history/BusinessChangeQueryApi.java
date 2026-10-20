package com.paytech.commercial.api.history;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.commercial.api.history.response.BusinessChangeSummary;
import com.paytech.foundation.web.paging.PagingResponseBody;

/**
 * commercial 业务变更历史（BCR）只读接口（运营控制台第二切片 technique.md §2；仅私网，调用方 operations-console-bff）
 * <li>按聚合根返回 Root 与全部受审计 Child 的不可变快照；快照内容按 record_type 白名单生成，不含凭据或敏感明文。</li>
 * <li>字段级差异由 BFF 比较同一 recordType + recordId 的相邻快照。只读，不加锁、不开事务。</li>
 */
@HttpExchange(url = "/business-changes/v1", accept = "application/json")
public interface BusinessChangeQueryApi {
    /**
     * 分页读取一个聚合根的变更快照，按 versionNo 倒序；同一版本内 Root 在前，其余按历史身份升序
     *
     * @param aggregateRootType 聚合根类型：AGREEMENT、COMMERCIAL_TERM_PLAN、PAYMENT_METHOD_PROFILE（定价画像）
     * @param aggregateRootId 聚合根 ID，规范正十进制
     * @param page 可选页码
     * @param size 可选每页条数
     * @return 快照分页；聚合根没有历史时为空页；缺少参数、未知类型或非规范 ID 400
     */
    @GetExchange
    PagingResponseBody<BusinessChangeSummary> listChanges(
        @RequestParam("aggregateRootType") String aggregateRootType,
        @RequestParam("aggregateRootId") String aggregateRootId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);
}
