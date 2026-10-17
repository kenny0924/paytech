package com.paytech.foundation.common.auto.web;

import com.paytech.foundation.web.paging.PagingResponseBody;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PagingResponseBodyTest {

    @Test
    void test_mappingChangesItemsAndPreservesPageMetadata() {
        PagingResponseBody<Integer> source = new PagingResponseBody<>(List.of(7, 3), 2, 10, 3_000_000_000L);

        PagingResponseBody<String> response = source.map(String::valueOf);

        Assertions.assertAll(
            () -> Assertions.assertEquals(List.of("7", "3"), response.items()),
            () -> Assertions.assertEquals(2, response.page()),
            () -> Assertions.assertEquals(10, response.size()),
            () -> Assertions.assertEquals(3_000_000_000L, response.total()),
            () -> Assertions.assertEquals(List.of(7, 3), source.items())
        );
    }

    @Test
    void test_emptyPageBeyondTotalPreservesRequestedPage() {
        PagingResponseBody<String> response = new PagingResponseBody<Integer>(List.of(), 2, 10, 1)
            .map(String::valueOf);

        Assertions.assertEquals(new PagingResponseBody<String>(List.of(), 2, 10, 1), response);
    }

    @Test
    void test_itemsAreAnImmutableSnapshot() {
        List<Integer> items = new ArrayList<>(List.of(7));
        PagingResponseBody<Integer> response = new PagingResponseBody<>(items, 1, 10, 1);

        items.add(8);

        Assertions.assertEquals(List.of(7), response.items());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> response.items().add(9));
        Assertions.assertThrows(NullPointerException.class, () -> new PagingResponseBody<>(null, 1, 10, 0));
    }
}
