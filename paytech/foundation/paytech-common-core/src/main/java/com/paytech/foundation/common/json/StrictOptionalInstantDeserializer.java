package com.paytech.foundation.common.json;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Binds an optional {@link Instant} only from a non-null ISO-8601 JSON string. */
public final class StrictOptionalInstantDeserializer extends ValueDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return context.reportInputMismatch(Instant.class, "an Instant must be an ISO-8601 JSON string");
        }
        try {
            return Instant.parse(parser.getString());
        } catch (DateTimeParseException exception) {
            return context.reportInputMismatch(Instant.class, "an Instant must be a valid ISO-8601 value");
        }
    }

    @Override
    public Instant getAbsentValue(DeserializationContext context) {
        return null;
    }

    @Override
    public Instant getNullValue(DeserializationContext context) {
        return context.reportInputMismatch(Instant.class, "explicit null is not allowed");
    }
}
