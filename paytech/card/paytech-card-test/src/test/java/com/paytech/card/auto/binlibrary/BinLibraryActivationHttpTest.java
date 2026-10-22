package com.paytech.card.auto.binlibrary;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.paytech.card.api.binlibrary.CardBinLibraryApi;
import com.paytech.card.api.enums.BinLibraryActivationType;
import com.paytech.card.api.enums.BinLibraryVersionStatus;
import com.paytech.card.api.enums.CardBrand;
import com.paytech.card.test.base.AbstractBinLibraryTest;

/**
 * BIN 库激活与回切：经 card 真实 HTTP 入口、Redis 资源锁 card-bin-library、版本 CAS 与生效占位唯一索引。
 *
 * <p>
 * 覆盖：激活 VALID 版本后查询立即用新版本、原版本 RETIRED，回切 RETIRED 版本后查询回到旧版本（AC-06），激活历史记录类型、版本对与操作人（B6）；
 * 期望生效版本不符 409；已生效、INVALID、VALIDATING 400；版本不存在 404；两个运营人员同时激活不同版本只有一个成功，库中始终只有一个 ACTIVE。
 *
 * @see CardBinLibraryApi#activateVersion(String, String, String, com.paytech.card.api.binlibrary.request.ActivateBinLibraryVersionRequestBody)
 * @see CardBinLibraryApi#listActivations(String, Integer, Integer)
 */
class BinLibraryActivationHttpTest extends AbstractBinLibraryTest {

    /** 激活 v2 后查询用 v2、v1 停用；回切 v1 后查询回到 v1；历史倒序两条。 */
    @Test
    void test_activateAndRollback() {
        long v1 = seedActiveVersion();
        long v2 = binLibrary.version(2, BinLibraryVersionStatus.VALID, 1);
        binLibrary.range(v2, "411111", CardBrand.MASTERCARD, "AE", "GCC");

        var activated = activate(2, 1);
        var afterActivate = json(lookup("41111100"));
        var rolledBack = activate(1, 2);
        var afterRollback = json(lookup("41111100"));

        var history = items(ops(HttpMethod.GET, "/activations", null));
        assertAll(() -> assertEquals(200, activated.status()),
            () -> assertEquals(BinLibraryActivationType.ACTIVATE.getCode(), json(activated).get("activationType")),
            () -> assertEquals(1, json(activated).get("previousVersionNo")), () -> assertEquals(ACTOR, json(activated).get("operatorAccountId")),
            () -> assertEquals(CardBrand.MASTERCARD.getCode(), afterActivate.get("cardBrand")),
            () -> assertEquals(2, afterActivate.get("binLibraryVersionNo")),
            () -> assertEquals(BinLibraryActivationType.ROLLBACK.getCode(), json(rolledBack).get("activationType")),
            () -> assertEquals(CardBrand.VISA.getCode(), afterRollback.get("cardBrand")),
            () -> assertEquals(1, afterRollback.get("binLibraryVersionNo")),
            () -> assertEquals(List.of(BinLibraryVersionStatus.ACTIVE.getCode(), BinLibraryVersionStatus.RETIRED.getCode()),
                List.of(version(1).get("status"), version(2).get("status"))),
            () -> assertNull(version(2).get("activeSlot")),
            () -> assertEquals(List.of(BinLibraryActivationType.ROLLBACK.getCode(), BinLibraryActivationType.ACTIVATE.getCode()),
                history.stream().map(row -> row.get("activationType")).toList()),
            () -> assertEquals(List.of(1, 2), history.stream().map(row -> row.get("versionNo")).toList()),
            () -> assertEquals(List.of(2, 1), history.stream().map(row -> row.get("previousVersionNo")).toList()),
            () -> assertEquals(List.of(v2, v1), mapper.binActivations().stream()
                .map(row -> ((Number)row.get("versionId")).longValue()).toList()));
    }

    /** 首次激活：没有生效版本时期望值为 null，原版本为空。 */
    @Test
    void test_activateFirstVersion() {
        binLibrary.version(1, BinLibraryVersionStatus.VALID, 0);

        var activated = activate(1, null);

        assertAll(() -> assertEquals(200, activated.status()), () -> assertNull(json(activated).get("previousVersionNo")),
            () -> assertEquals(BinLibraryVersionStatus.ACTIVE.getCode(), version(1).get("status")),
            () -> assertEquals(1, ((Number)version(1).get("activeSlot")).intValue()));
    }

    /** 期望生效版本不符 409；目标已生效、校验失败或仍在校验 400；版本不存在 404；失败不改变任何版本。 */
    @Test
    void test_rejectedActivations() {
        seedActiveVersion();
        binLibrary.version(2, BinLibraryVersionStatus.VALID, 0);
        binLibrary.version(3, BinLibraryVersionStatus.INVALID, 0);
        binLibrary.version(4, BinLibraryVersionStatus.VALIDATING, 0);

        var staleExpected = activate(2, null);
        var wrongExpected = activate(2, 3);
        var alreadyActive = activate(1, 1);
        var invalid = activate(3, 1);
        var validating = activate(4, 1);
        var missing = activate(9, 1);

        assertAll(() -> assertEquals(List.of(409, 409, 400, 400, 400, 404), List.of(staleExpected.status(),
            wrongExpected.status(), alreadyActive.status(), invalid.status(), validating.status(), missing.status())),
            () -> assertTrue(staleExpected.body().contains("CONCURRENT_REQUEST")),
            () -> assertEquals(List.of(BinLibraryVersionStatus.ACTIVE.getCode(), BinLibraryVersionStatus.VALID.getCode()),
                List.of(version(1).get("status"), version(2).get("status"))),
            () -> assertTrue(mapper.binActivations().isEmpty()));
    }

    /** 两个运营人员基于同一页面同时激活不同版本：只有一个成功，另一个 409，库中只有一个 ACTIVE。 */
    @Test
    void test_concurrentActivation() throws Exception {
        seedActiveVersion();
        binLibrary.version(2, BinLibraryVersionStatus.VALID, 0);
        binLibrary.version(3, BinLibraryVersionStatus.VALID, 0);
        var start = new CountDownLatch(1);

        var second = CompletableFuture.supplyAsync(() -> awaitThen(start, () -> activate(2, 1)));
        var third = CompletableFuture.supplyAsync(() -> awaitThen(start, () -> activate(3, 1)));
        start.countDown();

        var statuses = List.of(second.get(15, TimeUnit.SECONDS).status(), third.get(15, TimeUnit.SECONDS).status());
        assertAll(() -> assertEquals(List.of(200, 409), statuses.stream().sorted().toList()),
            () -> assertEquals(1, mapper.binVersions().stream()
                .filter(row -> BinLibraryVersionStatus.ACTIVE.getCode().equals(row.get("status"))).count()),
            () -> assertEquals(1, mapper.binActivations().size()));
    }

    /** 等待起跑信号后执行。 */
    static Response awaitThen(CountDownLatch start, java.util.function.Supplier<Response> action) {
        try {
            start.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
        return action.get();
    }
}
