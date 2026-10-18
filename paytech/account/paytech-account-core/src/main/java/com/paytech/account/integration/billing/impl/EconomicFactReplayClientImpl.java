package com.paytech.account.integration.billing.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.account.domain.BookIdentity;
import com.paytech.account.domain.FactRecord;
import com.paytech.account.integration.billing.EconomicFactReplayClient;
import com.paytech.billing.api.EconomicFactCommandApi;
import com.paytech.billing.api.response.EconomicFactLineResponseBody;
import com.paytech.billing.api.response.EconomicFactListResponseBody;
import com.paytech.billing.api.response.EconomicFactResponseBody;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 只适配 billing-api 经济事实列表 DTO（第 11a 步游标分页合同）；字段不合法时以系统错误失败关闭。 */
@Component
@AllArgsConstructor
public class EconomicFactReplayClientImpl implements EconomicFactReplayClient {
    /** 每页条数，billing 上限 500。 */
    static final String PAGE_SIZE = "500";

    /** 防御性分页上限，避免游标不前进时无限循环。 */
    static final int MAX_PAGES = 10_000;

    private final EconomicFactCommandApi economicFactApi;

    /** 按游标读完全部页；游标必须单调前进。 */
    @Override
    public List<FactRecord> listFacts(BookIdentity book) {
        List<FactRecord> facts = new ArrayList<>();
        String cursor = "0";
        for (int page = 0; page < MAX_PAGES; page++) {
            EconomicFactListResponseBody body = economicFactApi.listEconomicFacts(book.entityType().getCode(),
                book.entityId(), book.currency(), cursor, PAGE_SIZE);
            if (body == null || body.getItems() == null) {
                throw new ErrorException("billing 经济事实列表响应无效: " + book);
            }
            body.getItems().forEach(item -> facts.add(fact(item)));
            if (body.getNextFromFactId() == null) {
                return facts;
            }
            if (Long.parseLong(body.getNextFromFactId()) <= Long.parseLong(cursor)) {
                throw new ErrorException("billing 经济事实列表游标未前进: " + book);
            }
            cursor = body.getNextFromFactId();
        }
        throw new ErrorException("billing 经济事实列表超过分页上限: " + book);
    }

    /** 转换一条事实。 */
    FactRecord fact(EconomicFactResponseBody item) {
        try {
            List<FactRecord.Line> lines = item.getLines().stream().map(this::line).toList();
            return new FactRecord(Long.parseLong(item.getFactId()), Long.parseLong(item.getFactVersion()),
                longOrNull(item.getSupersedesFactId()), item.getFactType(), longOrNull(item.getClientId()),
                item.getEntityNo(), longOrNull(item.getLocalPartnerId()), longOrNull(item.getFinanceEntityId()),
                item.getCurrency(), new BigDecimal(item.getGrossAmount()), longOrNull(item.getChannelAccountId()), lines);
        } catch (RuntimeException invalid) {
            throw new ErrorException("billing 经济事实字段无效: " + item.getFactId(), invalid);
        }
    }

    /** 转换一行明细。 */
    FactRecord.Line line(EconomicFactLineResponseBody line) {
        return new FactRecord.Line(Integer.parseInt(line.getLineNo()), line.getFeeTypeCode(), line.getPayerRole(),
            line.getPayerId(), line.getPayeeRole(), line.getPayeeId(), line.getDirection(), new BigDecimal(line.getAmount()));
    }

    /** 可空十进制标识。 */
    static Long longOrNull(String value) {
        return value == null ? null : Long.valueOf(value);
    }
}
