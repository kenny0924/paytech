package com.paytech.account.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.ledger.api.response.LedgerAccountTotalResponseBody;
import com.paytech.ledger.api.response.LedgerAccountTotalsResponseBody;
import com.paytech.ledger.api.response.LedgerBookDetailResponseBody;
import com.paytech.ledger.api.response.LedgerBookResponseBody;

/**
 * ledger-api Mock（账簿身份、按主体查账簿、Owner 账户分录聚合余额）：只配置响应 DTO 与故障，记录调用；未配置的调用记为意外
 * <li>{@link #mockUnavailable()} 让全部 ledger 调用返回 503，用于验证失败关闭。</li>
 */
@TestComponent
public class LedgerBookMocker implements Clear {
    /** 故障响应的 Request ID。 */
    public static final String REQUEST_ID = "ledger-test";

    private final Map<String, LedgerBookDetailResponseBody> books = new HashMap<>();
    private final Map<String, List<LedgerAccountTotalResponseBody>> totals = new HashMap<>();
    private final List<String> calls = new ArrayList<>();
    private boolean unavailable;
    private int unexpected;

    /** 配置一个 OPEN 的 STANDARD 账簿。 */
    public synchronized void mockBook(long ledgerBookId, String entityType, String entityId, String currency) {
        LedgerBookDetailResponseBody book = new LedgerBookDetailResponseBody();
        book.setLedgerBookId(Long.toString(ledgerBookId));
        book.setEntityType(entityType);
        book.setEntityId(entityId);
        book.setCurrency(currency);
        book.setChartId("2001");
        book.setChartCode("STANDARD");
        book.setBookStatus("OPEN");
        books.put(book.getLedgerBookId(), book);
    }

    /** 配置账簿内 Owner 账户的分录聚合余额。 */
    public synchronized void mockAccountTotals(long ledgerBookId, List<LedgerAccountTotalResponseBody> items) {
        totals.put(Long.toString(ledgerBookId), List.copyOf(items));
    }

    /** 全部 ledger 调用返回 503。 */
    public synchronized void mockUnavailable() {
        unavailable = true;
    }

    /** 一个 Owner 账户的分录聚合余额 DTO。 */
    public static LedgerAccountTotalResponseBody total(long accountId, String accountCode, String ownerType,
        String ownerId, String bucket, String normalSide, String debit, String credit, String net) {
        LedgerAccountTotalResponseBody item = new LedgerAccountTotalResponseBody();
        item.setAccountId(Long.toString(accountId));
        item.setAccountCode(accountCode);
        item.setOwnerType(ownerType);
        item.setOwnerId(ownerId);
        item.setBalanceBucket(bucket);
        item.setNormalSide(normalSide);
        item.setDebitTotal(debit);
        item.setCreditTotal(credit);
        item.setNetBalance(net);
        item.setLastJournalSequence("1");
        return item;
    }

    /** {@code GET /ledger/v1/books/{bookId}}：未配置的账簿按 ledger 的业务拒绝返回 400。 */
    public synchronized LedgerBookDetailResponseBody book(String bookId) {
        calls.add("book:" + bookId);
        failIfUnavailable();
        LedgerBookDetailResponseBody book = books.get(bookId);
        if (book == null) {
            throw MockHttpFault.json(HttpStatus.BAD_REQUEST.value(), problem(400, "Bad Request", "账簿不存在: " + bookId,
                "BUSINESS_CHECK_FAILED"));
        }
        return book;
    }

    /** {@code GET /ledger-books/v1?entityType=&entityId=&currency=}：按主体与币种过滤已配置的账簿。 */
    public synchronized PagingResponseBody<LedgerBookResponseBody> listBooks(String entityType, String entityId,
        String currency) {
        calls.add("list:" + entityType + "/" + entityId + "/" + currency);
        failIfUnavailable();
        List<LedgerBookResponseBody> items = books.values().stream()
            .filter(book -> book.getEntityType().equals(entityType) && book.getEntityId().equals(entityId)
                && book.getCurrency().equals(currency))
            .map(LedgerBookMocker::listItem).toList();
        return new PagingResponseBody<>(items, 1, 10, items.size());
    }

    /** {@code GET /ledger/v1/books/{bookId}/account-totals}：未配置余额的已知账簿返回空列表。 */
    public synchronized LedgerAccountTotalsResponseBody accountTotals(String bookId) {
        calls.add("totals:" + bookId);
        failIfUnavailable();
        LedgerBookDetailResponseBody book = books.get(bookId);
        if (book == null) {
            unexpected++;
            throw MockHttpFault.json(HttpStatus.INTERNAL_SERVER_ERROR.value(), problem(500, "Internal Server Error",
                "未配置的 account-totals 查询", "INTERNAL_ERROR"));
        }
        LedgerAccountTotalsResponseBody body = new LedgerAccountTotalsResponseBody();
        body.setLedgerBookId(bookId);
        body.setEntityType(book.getEntityType());
        body.setEntityId(book.getEntityId());
        body.setCurrency(book.getCurrency());
        body.setItems(totals.getOrDefault(bookId, List.of()));
        return body;
    }

    /** 记录一次不在本 Mock 范围内的 ledger 调用。 */
    public synchronized MockHttpFault unexpectedCall(String operation) {
        unexpected++;
        calls.add(operation);
        return MockHttpFault.json(HttpStatus.INTERNAL_SERVER_ERROR.value(), problem(500, "Internal Server Error",
            "account 不应调用 " + operation, "INTERNAL_ERROR"));
    }

    /** 已记录的调用。 */
    public synchronized List<String> calls() {
        return List.copyOf(calls);
    }

    /** 全局 AfterEach：不允许出现未配置的调用。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(0, unexpected, "存在未配置的 ledger 调用: " + calls);
    }

    /** 每例清除配置与调用记录。 */
    @Override
    public synchronized void clear() {
        books.clear();
        totals.clear();
        calls.clear();
        unavailable = false;
        unexpected = 0;
    }

    /** 不可用时返回 503。 */
    void failIfUnavailable() {
        if (unavailable) {
            throw MockHttpFault.json(HttpStatus.SERVICE_UNAVAILABLE.value(), problem(503, "Service Unavailable", "合成故障",
                "SERVICE_UNAVAILABLE"));
        }
    }

    /** 列表项。 */
    static LedgerBookResponseBody listItem(LedgerBookDetailResponseBody book) {
        LedgerBookResponseBody item = new LedgerBookResponseBody();
        item.setLedgerBookId(book.getLedgerBookId());
        item.setEntityType(book.getEntityType());
        item.setEntityId(book.getEntityId());
        item.setCurrency(book.getCurrency());
        item.setChartCode(book.getChartCode());
        item.setBookStatus(book.getBookStatus());
        return item;
    }

    /** Problem 响应体。 */
    static ApiProblemResponse problem(int status, String title, String detail, String code) {
        return new ApiProblemResponse(ApiProblemProfile.TYPE, title, detail, code, REQUEST_ID,
            "2026-10-18T00:00:00.000000Z", null);
    }
}
