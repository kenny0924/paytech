package com.paytech.foundation.redis.core.serialization;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.paytech.foundation.common.enums.CodeEnum;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.deser.Deserializers;
import tools.jackson.databind.deser.KeyDeserializers;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.Serializers;

/**
 * 让 Redis JSON 中的项目枚举稳定使用 {@link CodeEnum#getCode()}，避免持久化可被重构的枚举名称。
 *
 * @author Codex
 */
public final class CodeEnumJsonModule extends SimpleModule {

    private static final long serialVersionUID = 1L;

    public CodeEnumJsonModule() {
        super("paytech-code-enum");
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        context.addSerializers(new CodeEnumSerializers());
        context.addDeserializers(new CodeEnumDeserializers());
        context.addKeySerializers(new CodeEnumKeySerializers());
        context.addKeyDeserializers(new CodeEnumKeyDeserializers());
    }

    private static final class CodeEnumKeySerializers extends Serializers.Base {

        @Override
        public ValueSerializer<?> findSerializer(SerializationConfig config, JavaType type,
            BeanDescription.Supplier beanDescription, JsonFormat.Value format) {
            if (!CodeEnum.class.isAssignableFrom(type.getRawClass())) {
                return null;
            }
            return CodeEnumKeySerializer.INSTANCE;
        }
    }

    private static final class CodeEnumKeySerializer extends ValueSerializer<CodeEnum> {

        private static final CodeEnumKeySerializer INSTANCE = new CodeEnumKeySerializer();

        @Override
        public void serialize(CodeEnum value, JsonGenerator generator, SerializationContext context) {
            generator.writeName(value.getCode());
        }
    }

    private static final class CodeEnumSerializers extends Serializers.Base {

        @Override
        public ValueSerializer<?> findEnumSerializer(SerializationConfig config, JavaType type,
            BeanDescription.Supplier beanDescription, JsonFormat.Value format) {
            if (!CodeEnum.class.isAssignableFrom(type.getRawClass())) {
                return null;
            }
            return CodeEnumSerializer.INSTANCE;
        }
    }

    private static final class CodeEnumKeyDeserializers implements KeyDeserializers {

        @Override
        public KeyDeserializer findKeyDeserializer(JavaType type, DeserializationConfig config,
            BeanDescription.Supplier beanDescription) {
            Class<?> valueType = type.getRawClass();
            if (!valueType.isEnum() || !CodeEnum.class.isAssignableFrom(valueType)) {
                return null;
            }
            return new CodeEnumKeyDeserializer(valueType);
        }
    }

    private static final class CodeEnumKeyDeserializer extends KeyDeserializer {

        private final Class<?> valueType;

        private CodeEnumKeyDeserializer(Class<?> valueType) {
            this.valueType = valueType;
        }

        @Override
        public Object deserializeKey(String code, DeserializationContext context) {
            for (Object enumConstant : valueType.getEnumConstants()) {
                CodeEnum codeEnum = (CodeEnum)enumConstant;
                if (codeEnum.getCode().equals(code)) {
                    return enumConstant;
                }
            }
            return context.handleWeirdKey(valueType, code, "未找到匹配的CodeEnum Code");
        }
    }

    private static final class CodeEnumSerializer extends ValueSerializer<CodeEnum> {

        private static final CodeEnumSerializer INSTANCE = new CodeEnumSerializer();

        @Override
        public void serialize(CodeEnum value, JsonGenerator generator, SerializationContext context) {
            generator.writeString(value.getCode());
        }
    }

    private static final class CodeEnumDeserializers extends Deserializers.Base {

        @Override
        public boolean hasDeserializerFor(DeserializationConfig config, Class<?> valueType) {
            return valueType.isEnum() && CodeEnum.class.isAssignableFrom(valueType);
        }

        @Override
        public ValueDeserializer<?> findEnumDeserializer(JavaType type, DeserializationConfig config,
            BeanDescription.Supplier beanDescription) {
            Class<?> valueType = type.getRawClass();
            if (!hasDeserializerFor(config, valueType)) {
                return null;
            }
            return new CodeEnumDeserializer(valueType);
        }
    }

    private static final class CodeEnumDeserializer extends ValueDeserializer<Enum<?>> {

        private final Class<?> valueType;

        private CodeEnumDeserializer(Class<?> valueType) {
            this.valueType = valueType;
        }

        @Override
        public Enum<?> deserialize(JsonParser parser, DeserializationContext context) {
            String code = parser.getValueAsString();
            if (code == null) {
                return context.reportInputMismatch(valueType, "CodeEnum必须使用字符串Code");
            }
            for (Object enumConstant : valueType.getEnumConstants()) {
                CodeEnum codeEnum = (CodeEnum)enumConstant;
                if (codeEnum.getCode().equals(code)) {
                    return (Enum<?>)enumConstant;
                }
            }
            return (Enum<?>)context.handleWeirdStringValue(valueType, code, "未找到匹配的CodeEnum Code");
        }
    }
}
