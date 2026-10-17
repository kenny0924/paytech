package com.paytech.foundation.common.auto.json;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.foundation.common.json.RejectExplicitNullDeserializer;
import com.paytech.foundation.common.json.StrictOptionalInstantDeserializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.json.JsonMapper;

/** 通用请求 JSON 绑定策略的可执行契约。 */
class RequestJsonBindingAutoTest {

    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void test_optionalValuesMayBeAbsentAndValidValuesUseTheirNativeDeserializers() {
        OptionalValues absent = mapper.readValue("{}", OptionalValues.class);
        Assertions.assertAll(
            () -> Assertions.assertNull(absent.text()),
            () -> Assertions.assertNull(absent.amount()),
            () -> Assertions.assertNull(absent.timestamp()));

        OptionalValues present = mapper.readValue("""
            {"text":"value","amount":12.50,"timestamp":"2026-09-08T12:34:56Z"}
            """, OptionalValues.class);
        Assertions.assertAll(
            () -> Assertions.assertEquals("value", present.text()),
            () -> Assertions.assertEquals(new BigDecimal("12.50"), present.amount()),
            () -> Assertions.assertEquals(Instant.parse("2026-09-08T12:34:56Z"), present.timestamp()));
    }

    @Test
    void test_explicitNullIsRejectedForEveryWrappedType() {
        for (String property : new String[] {"text", "amount", "timestamp"}) {
            String json = "{\"" + property + "\":null}";
            Assertions.assertThrows(JacksonException.class, () -> mapper.readValue(json, OptionalValues.class),
                property);
        }
    }

    @Test
    void test_strictOptionalInstantAcceptsOnlyAnIso8601String() {
        StrictInstant absent = mapper.readValue("{}", StrictInstant.class);
        Assertions.assertNull(absent.value());
        Assertions.assertEquals(Instant.parse("2026-09-08T12:34:56Z"),
            mapper.readValue("{\"value\":\"2026-09-08T12:34:56Z\"}", StrictInstant.class).value());

        for (String invalid : new String[] {"{\"value\":null}", "{\"value\":123}",
            "{\"value\":12.5}", "{\"value\":\"not-an-instant\"}"}) {
            Assertions.assertThrows(JacksonException.class, () -> mapper.readValue(invalid, StrictInstant.class),
                invalid);
        }
    }

    private record OptionalValues(
        @JsonDeserialize(using = RejectExplicitNullDeserializer.class) String text,
        @JsonDeserialize(using = RejectExplicitNullDeserializer.class) BigDecimal amount,
        @JsonDeserialize(using = RejectExplicitNullDeserializer.class) Instant timestamp) {
    }

    private record StrictInstant(
        @JsonDeserialize(using = StrictOptionalInstantDeserializer.class) Instant value) {
    }
}
