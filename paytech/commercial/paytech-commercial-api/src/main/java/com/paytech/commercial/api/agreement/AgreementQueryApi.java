package com.paytech.commercial.api.agreement;

import java.util.List;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.commercial.api.agreement.response.AgreementSummary;
import com.paytech.foundation.web.paging.PagingResponseBody;

/**
 * 合同列表查询（运营控制台第二切片 technique.md §2；仅私网，调用方 operations-console-bff）
 * <li>只读，不加锁、不开事务；分页复用公共 page/size：page 默认 1（1–100000），size 默认 10 且只允许 10、20、50、100、200，
 * 非法值 400 INVALID_REQUEST。</li>
 */
@HttpExchange(url = "/agreements/v1", accept = "application/json")
public interface AgreementQueryApi {
    /**
     * 按条件分页查询合同，按创建时间、合同 ID 倒序
     *
     * @param clientId 可选 Client ID，规范正十进制
     * @param localPartnerId 可选 Local Partner ID，规范正十进制
     * @param agreementType 可选合同类型：CLIENT、PARTNER
     * @param status 可选合同状态，可重复：DRAFT、PENDING_SIGNATURE、SIGNED、ACTIVE、EXPIRED、TERMINATED、CANCELLED
     * @param entityNo 可选 OE 编号；匹配 CLIENT 合同的 OE 与 PARTNER 合同的覆盖 OE
     * @param agreementNo 可选合同编号前缀，按字面匹配（不支持通配符）
     * @param page 可选页码
     * @param size 可选每页条数
     * @return 合同分页；changeVersion 为合同 BCR Root 当前版本；未知代码或非规范 ID 400
     */
    @GetExchange
    PagingResponseBody<AgreementSummary> listAgreements(
        @RequestParam(value = "clientId", required = false) String clientId,
        @RequestParam(value = "localPartnerId", required = false) String localPartnerId,
        @RequestParam(value = "agreementType", required = false) String agreementType,
        @RequestParam(value = "status", required = false) List<String> status,
        @RequestParam(value = "entityNo", required = false) String entityNo,
        @RequestParam(value = "agreementNo", required = false) String agreementNo,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);
}
