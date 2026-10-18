package com.paytech.gateway.test.simulator.jwks;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.springframework.http.HttpMethod;

/** 记录并显式核验 JWKS Simulator 收到的真实 HTTP 请求。 */
public final class JwksRequestJournal {
    private final List<Entry> entries = new ArrayList<>();
    private int verifiedEntries;

    public synchronized void record(HttpMethod method, String path) {
        entries.add(new Entry(method, path));
    }

    public synchronized int count() {
        return entries.size();
    }

    public synchronized void verifySingleGet() {
        verifyGets(1);
    }

    public synchronized void verifyGets(int expectedCount) {
        verifyGetsAtPath(expectedCount, JwksSimulator.JWKS_PATH);
    }

    public synchronized void verifyCustomerGets(int expectedCount) {
        verifyGetsAtPath(expectedCount, JwksSimulator.CUSTOMER_JWKS_PATH);
    }

    private void verifyGetsAtPath(int expectedCount, String path) {
        Assertions.assertEquals(expectedCount, entries.size(), () -> "JWKS 请求数量不符合预期: " + entries);
        Assertions.assertTrue(
            entries.stream().allMatch(entry -> entry.equals(new Entry(HttpMethod.GET, path))),
            () -> "JWKS 请求记录不符合预期: " + entries);
        verifiedEntries = entries.size();
    }

    public synchronized void verifyNoRequests() {
        Assertions.assertTrue(entries.isEmpty(), () -> "JWKS 不应收到请求: " + entries);
        verifiedEntries = 0;
    }

    public synchronized void reset() {
        entries.clear();
        verifiedEntries = 0;
    }

    public synchronized void verifyComplete() {
        Assertions.assertEquals(entries.size(), verifiedEntries,
            () -> "JWKS Request Journal 存在未核验请求: " + entries.subList(verifiedEntries, entries.size()));
    }

    /** JWKS Simulator 收到的一次请求。 */
    public record Entry(HttpMethod method, String path) {
    }
}
