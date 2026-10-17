package com.paytech.foundation.common.json;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;

/**
 * Preserves the target type's normal Jackson binding while allowing an absent property and rejecting an explicit
 * JSON {@code null}.
 */
public final class RejectExplicitNullDeserializer extends ValueDeserializer<Object> {

    private final JavaType targetType;

    private final ValueDeserializer<Object> delegate;

    /** Constructor used by Jackson before property contextualization. */
    public RejectExplicitNullDeserializer() {
        this(null, null);
    }

    private RejectExplicitNullDeserializer(JavaType targetType, ValueDeserializer<Object> delegate) {
        this.targetType = targetType;
        this.delegate = delegate;
    }

    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext context, BeanProperty property) {
        if (property == null) {
            throw new IllegalStateException("RejectExplicitNullDeserializer must be used on a JSON property");
        }
        JavaType propertyType = property.getType();
        ValueDeserializer<Object> rawDelegate = context.findNonContextualValueDeserializer(propertyType);
        @SuppressWarnings("unchecked")
        ValueDeserializer<Object> contextualDelegate = (ValueDeserializer<Object>)context
            .handleSecondaryContextualization(rawDelegate, property, propertyType);
        return new RejectExplicitNullDeserializer(propertyType, contextualDelegate);
    }

    @Override
    public Object deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        return delegate.deserialize(parser, context);
    }

    @Override
    public Object getAbsentValue(DeserializationContext context) {
        return null;
    }

    @Override
    public Object getNullValue(DeserializationContext context) {
        return context.reportInputMismatch(targetType, "explicit null is not allowed");
    }
}
