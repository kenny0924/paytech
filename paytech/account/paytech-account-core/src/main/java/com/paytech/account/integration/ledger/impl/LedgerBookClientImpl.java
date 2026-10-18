package com.paytech.account.integration.ledger.impl;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.core.enums.BookEntityType;
import com.paytech.account.domain.BookIdentity;
import com.paytech.account.domain.LedgerAccountTotal;
import com.paytech.account.domain.LedgerBookRef;
import com.paytech.account.integration.ledger.LedgerBookClient;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.ledger.api.LedgerBookQueryApi;
import com.paytech.ledger.api.LedgerReadApi;
import com.paytech.ledger.api.response.LedgerAccountTotalResponseBody;
import com.paytech.ledger.api.response.LedgerAccountTotalsResponseBody;
import com.paytech.ledger.api.response.LedgerBookDetailResponseBody;
import com.paytech.ledger.api.response.LedgerBookResponseBody;

import lombok.AllArgsConstructor;

/** 只适配 ledger-api DTO；HTTP、MDC 与错误解码交给 Foundation 公共客户端，响应不合法时以系统错误失败关闭。 */
@Component
@AllArgsConstructor
public class LedgerBookClientImpl implements LedgerBookClient {
    private final LedgerReadApi ledgerReadApi;
    private final LedgerBookQueryApi ledgerBookQueryApi;

    /** 响应必须回显请求的账簿。 */
    @Override
    public LedgerBookRef getBook(long ledgerBookId) {
        LedgerBookDetailResponseBody body = ledgerReadApi.getLedgerBook(Long.toString(ledgerBookId));
        if (body == null || !Long.toString(ledgerBookId).equals(body.getLedgerBookId())) {
            throw new ErrorException("ledger 账簿响应无效: " + ledgerBookId);
        }
        return ref(body.getLedgerBookId(), body.getEntityType(), body.getEntityId(), body.getCurrency());
    }

    /** 主体与币种唯一确定账簿；返回多个视为 ledger 数据不一致。 */
    @Override
    public LedgerBookRef findOperatingEntityBook(String entityNo, String currency) {
        PagingResponseBody<LedgerBookResponseBody> page = ledgerBookQueryApi.listLedgerBooks(
            BookEntityType.OPERATING_ENTITY.getCode(), entityNo, currency, 1, 10);
        if (page == null || page.items() == null) {
            throw new ErrorException("ledger 账簿列表响应无效: " + entityNo + "/" + currency);
        }
        if (page.items().size() > 1) {
            throw new ErrorException("ledger 经营主体币种账簿不唯一: " + entityNo + "/" + currency);
        }
        if (page.items().isEmpty()) {
            return null;
        }
        LedgerBookResponseBody book = page.items().getFirst();
        LedgerBookRef ref = ref(book.getLedgerBookId(), book.getEntityType(), book.getEntityId(), book.getCurrency());
        if (!ref.identity().equals(BookIdentity.operatingEntity(entityNo, currency))) {
            throw new ErrorException("ledger 账簿列表响应与查询条件不一致: " + entityNo + "/" + currency);
        }
        return ref;
    }

    /** 响应必须回显请求的账簿。 */
    @Override
    public List<LedgerAccountTotal> accountTotals(LedgerBookRef book) {
        LedgerAccountTotalsResponseBody body = ledgerReadApi.getAccountTotals(Long.toString(book.ledgerBookId()));
        if (body == null || body.getItems() == null || !Long.toString(book.ledgerBookId()).equals(body.getLedgerBookId())) {
            throw new ErrorException("ledger account-totals 响应无效: " + book.ledgerBookId());
        }
        return body.getItems().stream().map(this::total).toList();
    }

    /** 转换一个账户余额。 */
    LedgerAccountTotal total(LedgerAccountTotalResponseBody item) {
        return new LedgerAccountTotal(Long.parseLong(item.getAccountId()), item.getAccountCode(),
            OwnerType.valueOf(item.getOwnerType()), item.getOwnerId(), BalanceBucket.valueOf(item.getBalanceBucket()),
            new BigDecimal(item.getNetBalance()));
    }

    /** 账簿身份。 */
    LedgerBookRef ref(String ledgerBookId, String entityType, String entityId, String currency) {
        try {
            return new LedgerBookRef(Long.parseLong(ledgerBookId),
                new BookIdentity(BookEntityType.valueOf(entityType), entityId, currency));
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new ErrorException("ledger 账簿响应字段无效: " + ledgerBookId, invalid);
        }
    }
}
