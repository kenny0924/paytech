package com.paytech.foundation.httpclient.auto;

import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.test.simulator.HttpServiceSimulator;
import com.paytech.foundation.web.paging.PagingResponseBody;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.annotation.GetExchange;
import tools.jackson.databind.json.JsonMapper;

/**
 * 验证分页四字段 JSON 及真实 HTTP 接口代理的泛型列表还原，不引入公共模型的 JSON 运行时依赖。
 *
 * @author Codex
 */
class PagingResponseBodyJsonTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void test_jsonContainsOnlyStablePagingFields() {
        PagingResponseBody<ResourceItem> response = new PagingResponseBody<>(
            List.of(new ResourceItem("9007199254740993")), 2, 10, 3_000_000_000L);

        String json = mapper.writeValueAsString(response);

        Assertions.assertEquals(mapper.readTree("""
            {"items":[{"id":"9007199254740993"}],"page":2,"size":10,"total":3000000000}
            """), mapper.readTree(json));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void test_httpClientRestoresTypedItemsAndEmptyPageMetadata(boolean empty) throws IOException {
        List<ResourceItem> items = empty ? List.of() : List.of(new ResourceItem("9007199254740993"));
        PagingResponseBody<ResourceItem> expected = new PagingResponseBody<>(items, 2, 10, 3_000_000_000L);
        try (HttpServiceSimulator simulator = new HttpServiceSimulator()) {
            simulator.respond(200, mapper.writeValueAsString(expected), "application/json");

            PagingResponseBody<ResourceItem> actual = client(simulator).read();

            Assertions.assertEquals(expected, actual);
            Assertions.assertEquals(items.stream().map(ResourceItem::id).toList(), actual.map(ResourceItem::id).items());
            Assertions.assertEquals(List.of("GET /resource"), simulator.requests());
        }
    }

    private ResourceApi client(HttpServiceSimulator simulator) {
        HttpClient transport = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1))
            .followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(transport);
        requestFactory.setReadTimeout(Duration.ofSeconds(2));
        RestClient restClient = RestClient.builder().baseUrl(simulator.baseUrl()).requestFactory(requestFactory).build();
        return HttpServiceClientFactory.create(restClient).createClient(ResourceApi.class);
    }

    interface ResourceApi {
        @GetExchange("/resource")
        PagingResponseBody<ResourceItem> read();
    }

    record ResourceItem(String id) {}
}
