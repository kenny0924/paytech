package com.paytech.account.app.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.account.api.OwnerAccountQueryApi;
import com.paytech.account.api.response.OwnerAccountEntryResponseBody;
import com.paytech.account.api.response.OwnerAccountResponseBody;
import com.paytech.account.app.ext.rest.converter.OwnerAccountConverter;
import com.paytech.account.service.processor.ListAccountEntriesProcessor;
import com.paytech.account.service.processor.ListOwnerAccountsProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** Owner 账户与明细流水查询的 HTTP 协议适配；每个入口委托一个 Processor。 */
@RestController
@AllArgsConstructor
public class OwnerAccountQueryController implements OwnerAccountQueryApi {
    private final OwnerAccountConverter converter;
    private final ListOwnerAccountsProcessor accountsProcessor;
    private final ListAccountEntriesProcessor entriesProcessor;

    /** 分页查询 Owner 账户。 */
    @Override
    public PagingResponseBody<OwnerAccountResponseBody> listOwnerAccounts(String entityType, String entityId,
        String currency, String ownerType, String ownerId, String accountCode, String balanceBucket, Integer page,
        Integer size) {
        return accountsProcessor.process(converter.toListRequest(entityType, entityId, currency, ownerType, ownerId,
            accountCode, balanceBucket, page, size)).map(converter::toResponseBody);
    }

    /** 分页查询账户流水。 */
    @Override
    public PagingResponseBody<OwnerAccountEntryResponseBody> listOwnerAccountEntries(String accountId, Integer page,
        Integer size) {
        return entriesProcessor.process(converter.toEntriesRequest(accountId, page, size)).map(converter::toResponseBody);
    }
}
