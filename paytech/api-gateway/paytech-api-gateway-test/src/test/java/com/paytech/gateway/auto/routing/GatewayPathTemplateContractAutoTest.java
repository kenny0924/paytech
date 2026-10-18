package com.paytech.gateway.auto.routing;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import com.paytech.gateway.routing.runtime.GatewayPathTemplate;

/** 路径值对象合同：变量精确解码与规范重编码，不允许越过单 Segment 边界。 */
class GatewayPathTemplateContractAutoTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "relative", "/中文", "/a//b", "/a\\b", "/a?b", "/a#b", "/a/", "/.", "/..",
        "/{1bad}", "/{id}/{id}", "/{unfinished", "/unfinished}", "/[x", "/x]", "/(x", "/x)", "/x|y", "/^x",
        "/%2f", "/%5C", "/%41", "/%7E", "/%2D", "/%2E", "/%5F", "/%30", "/%61", "/%", "/a b"})
    void test_rejectsAmbiguousOrNonCanonicalTemplate(String path) {
        assertThrows(IllegalArgumentException.class, () -> GatewayPathTemplate.compile(path));
    }

    /** 非法请求路径、控制字符及无效 UTF-8 不能匹配变量模板。 */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"/items", "/other/123", "/items/", "/items/.", "/items/..", "/items/a/b",
        "/items/%2F", "/items/%5C", "/items/%00", "/items/%7F", "/items/%C2%85", "/items/%FF", "/items/{bad}",
        "/items/%E2%82", "/items/%41", "/items/%2f"})
    void test_unsafeOrMismatchedRequestDoesNotMatch(String rawPath) {
        assertTrue(GatewayPathTemplate.compile("/items/{id}").match(rawPath).isEmpty());
    }

    /** 合法变量解码后重写仍保持单段路径与不可变参数。 */
    @ParameterizedTest
    @CsvSource({"Ab09-._~,Ab09-._~", "%E2%82%AC,€", "%C3%A9,é", "%3A,:"})
    void test_roundTripsCanonicalVariable(String raw, String decoded) {
        var path = GatewayPathTemplate.compile("/items/{id}");
        var variables = path.match("/items/" + raw).orElseThrow();
        assertEquals(Map.of("id", decoded), variables);
        assertEquals("/items/" + raw, path.render(variables));
        assertThrows(UnsupportedOperationException.class, () -> variables.clear());
    }

    /** 根路径可匹配，超长模板或缺失变量必须明确拒绝。 */
    @Test
    void test_rootLengthAndMissingRenderVariable() {
        var root = GatewayPathTemplate.compile("/");
        assertEquals(Map.of(), root.match("/").orElseThrow());
        assertEquals("/", root.render(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> GatewayPathTemplate.compile("/" + "x".repeat(1_000)));
        assertThrows(IllegalArgumentException.class, () -> GatewayPathTemplate.compile("/{id}").render(Map.of()));
        assertEquals("/a%20b%2Fc", GatewayPathTemplate.compile("/{id}").render(Map.of("id", "a b/c")));
    }
}
