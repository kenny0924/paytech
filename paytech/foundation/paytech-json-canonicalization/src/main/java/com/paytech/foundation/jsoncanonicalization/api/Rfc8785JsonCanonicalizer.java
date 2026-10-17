package com.paytech.foundation.jsoncanonicalization.api;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.erdtman.jcs.JsonCanonicalizer;

import tools.jackson.core.ErrorReportConfiguration;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.json.JsonFactoryBuilder;
import tools.jackson.core.json.JsonReadFeature;

/** 为已批准 JSON Object Profile 生成 RFC 8785 规范 UTF-8 bytes。 */
public final class Rfc8785JsonCanonicalizer {

    private static final int MAX_DOCUMENT_BYTES = 1_048_576;
    private static final long MAX_TOKEN_COUNT = 100_000L;
    private static final int MAX_NESTING_DEPTH = 64;
    private static final int MAX_STRING_LENGTH = 262_144;
    private static final int MAX_PROPERTY_NAME_LENGTH = 512;
    private static final int MAX_ENCODED_PROPERTY_NAME_LENGTH = MAX_PROPERTY_NAME_LENGTH * 6;
    private static final int MAX_NUMBER_LENGTH = 16;
    private static final BigInteger MAX_SAFE_INTEGER = new BigInteger("9007199254740991");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("0|[1-9][0-9]*");

    private static final JsonFactory JSON_FACTORY = strictJsonFactory();

    private Rfc8785JsonCanonicalizer() {
    }

    public static byte[] canonicalizeObject(byte[] jsonUtf8) {
        if (jsonUtf8 == null || jsonUtf8.length == 0 || jsonUtf8.length > MAX_DOCUMENT_BYTES) {
            throw new JsonCanonicalizationException();
        }
        byte[] source = jsonUtf8.clone();
        validate(source);
        try {
            return new JsonCanonicalizer(source).getEncodedUTF8().clone();
        } catch (IOException | RuntimeException exception) {
            throw new JsonCanonicalizationException(exception);
        }
    }

    private static JsonFactory strictJsonFactory() {
        StreamReadConstraints constraints = StreamReadConstraints.builder()
            .maxDocumentLength(MAX_DOCUMENT_BYTES)
            .maxTokenCount(MAX_TOKEN_COUNT)
            .maxNestingDepth(MAX_NESTING_DEPTH)
            .maxStringLength(MAX_STRING_LENGTH)
            .maxNameLength(MAX_ENCODED_PROPERTY_NAME_LENGTH)
            .maxNumberLength(MAX_NUMBER_LENGTH)
            .build();
        ErrorReportConfiguration errors = ErrorReportConfiguration.builder()
            .maxErrorTokenLength(0)
            .maxRawContentLength(0)
            .build();
        JsonFactoryBuilder builder = JsonFactory.builder()
            .streamReadConstraints(constraints)
            .errorReportConfiguration(errors)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION);
        for (JsonReadFeature feature : JsonReadFeature.values()) {
            builder.disable(feature);
        }
        return builder.build();
    }

    private static void validate(byte[] jsonUtf8) {
        if (startsWithUtf8Bom(jsonUtf8)) {
            throw new JsonCanonicalizationException();
        }
        validateUtf8(jsonUtf8);
        validateJsonTokens(jsonUtf8);
    }

    private static boolean startsWithUtf8Bom(byte[] jsonUtf8) {
        return jsonUtf8.length >= 3
            && jsonUtf8[0] == (byte) 0xEF
            && jsonUtf8[1] == (byte) 0xBB
            && jsonUtf8[2] == (byte) 0xBF;
    }

    private static void validateUtf8(byte[] jsonUtf8) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(jsonUtf8));
        } catch (CharacterCodingException exception) {
            throw new JsonCanonicalizationException(exception);
        }
    }

    private static void validateJsonTokens(byte[] jsonUtf8) {
        try (JsonParser parser = JSON_FACTORY.createParser(ObjectReadContext.empty(), jsonUtf8)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new JsonCanonicalizationException();
            }
            int depth = 1;
            while (depth > 0) {
                JsonToken token = parser.nextToken();
                if (token == null) {
                    throw new JsonCanonicalizationException();
                }
                depth = validateToken(parser, token, depth);
            }
            if (parser.nextToken() != null) {
                throw new JsonCanonicalizationException();
            }
        } catch (JacksonException exception) {
            throw new JsonCanonicalizationException(exception);
        }
    }

    private static int validateToken(JsonParser parser, JsonToken token, int depth) throws JacksonException {
        return switch (token) {
            case START_OBJECT, START_ARRAY -> depth + 1;
            case END_OBJECT, END_ARRAY -> depth - 1;
            case PROPERTY_NAME -> {
                String propertyName = parser.getString();
                if (propertyName.length() > MAX_PROPERTY_NAME_LENGTH) {
                    throw new JsonCanonicalizationException();
                }
                validateSurrogates(propertyName);
                yield depth;
            }
            case VALUE_STRING -> {
                validateSurrogates(parser.getString());
                yield depth;
            }
            case VALUE_NUMBER_INT -> {
                validateNumber(parser.getString());
                yield depth;
            }
            case VALUE_TRUE, VALUE_FALSE, VALUE_NULL -> depth;
            case VALUE_NUMBER_FLOAT, VALUE_EMBEDDED_OBJECT, NOT_AVAILABLE ->
                throw new JsonCanonicalizationException();
        };
    }

    private static void validateSurrogates(String value) {
        for (int index = 0; index < value.length(); index++) {
            char codeUnit = value.charAt(index);
            if (Character.isHighSurrogate(codeUnit)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new JsonCanonicalizationException();
                }
                index++;
            } else if (Character.isLowSurrogate(codeUnit)) {
                throw new JsonCanonicalizationException();
            }
        }
    }

    private static void validateNumber(String numberText) {
        if (numberText.length() > MAX_NUMBER_LENGTH || !NUMBER_PATTERN.matcher(numberText).matches()
            || new BigInteger(numberText).compareTo(MAX_SAFE_INTEGER) > 0) {
            throw new JsonCanonicalizationException();
        }
    }
}
