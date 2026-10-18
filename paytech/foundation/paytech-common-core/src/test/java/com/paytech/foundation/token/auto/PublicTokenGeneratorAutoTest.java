package com.paytech.foundation.token.auto;

import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.token.PublicTokenGenerator;

/** 公共 Token 的无前缀格式、当前时间、历史校验和并发随机性合同。 */
class PublicTokenGeneratorAutoTest {
    private static final String LEGACY_BODY = "3Iu73V3AfDlneKkX3g6YoNctEljGRT";
    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    /** 生成入口只有可变混入值，不暴露业务前缀、时钟或随机源注入。 */
    @Test
    void test_generationApiIsStaticAndAcceptsOnlyStringVarargs() throws Exception {
        var method = PublicTokenGenerator.class.getMethod("generate", String[].class);
        Assertions.assertTrue(Modifier.isStatic(method.getModifiers()));
        Assertions.assertTrue(method.isVarArgs());
        Assertions.assertEquals(String.class, method.getReturnType());
        Assertions.assertEquals(0, PublicTokenGenerator.class.getConstructors().length);
    }

    /** 时间戳来自生成时的系统 UTC 秒，输出只含固定长度主体。 */
    @Test
    void test_generatedBodyUsesCurrentTimeWithoutPrefix() {
        long before = Instant.now().getEpochSecond();
        String token = PublicTokenGenerator.generate("US", "40001");
        long after = Instant.now().getEpochSecond();
        Assertions.assertTrue(token.matches("[0-9A-Za-z]{30}"));
        Assertions.assertTrue(PublicTokenGenerator.isValid(token));
        BigInteger value = BigInteger.ZERO;
        for (char character : token.substring(0, 27).toCharArray()) {
            value = value.multiply(BigInteger.valueOf(62)).add(BigInteger.valueOf(BASE62.indexOf(character)));
        }
        long seconds = value.shiftRight(128).longValueExact() + 1_400_000_000L;
        Assertions.assertTrue(seconds >= before && seconds <= after);
    }

    /** 老主体仍可校验；校验不需要原混入参数，也不判断业务有效期。 */
    @Test
    void test_legacyBodyRemainsValid() {
        Assertions.assertTrue(PublicTokenGenerator.isValid(LEGACY_BODY));
        Assertions.assertFalse(PublicTokenGenerator.isValid("chk_" + LEGACY_BODY));
        Assertions.assertFalse(PublicTokenGenerator.isValid(LEGACY_BODY.toLowerCase(java.util.Locale.ROOT)));
    }

    /** 长度、字符、原始字节范围和校验码错误均拒绝。 */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"3Iu73V3AfDlneKkX3g6YoNctEljGR", "3Iu73V3AfDlneKkX3g6YoNctEljGRT0",
        "3Iu73V3AfDlneKkX3g6YoNctEljGRU", "4Iu73V3AfDlneKkX3g6YoNctEljGRT",
        "_Iu73V3AfDlneKkX3g6YoNctEljGRT", "ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ"})
    void test_invalidBodiesAreRejected(String value) {
        Assertions.assertFalse(PublicTokenGenerator.isValid(value));
    }

    /** 混入值可省略、为空串或包含 Unicode；参数边界内均可生成。 */
    @Test
    void test_optionalMixingValuesRemainOpaque() {
        Assertions.assertTrue(PublicTokenGenerator.isValid(PublicTokenGenerator.generate()));
        Assertions.assertTrue(PublicTokenGenerator.isValid(PublicTokenGenerator.generate("", "客户", "a:b", "🚀")));
        String[] values = new String[16];
        java.util.Arrays.fill(values, "中".repeat(1024));
        Assertions.assertTrue(PublicTokenGenerator.isValid(PublicTokenGenerator.generate(values)));
    }

    /** 拒绝不明确或过大的混入输入，不隐式替换非法 Unicode。 */
    @Test
    void test_invalidMixingValuesFailClosed() {
        Assertions.assertThrows(InvalidParameterException.class, () -> PublicTokenGenerator.generate((String[])null));
        Assertions.assertThrows(InvalidParameterException.class, () -> PublicTokenGenerator.generate("US", null));
        Assertions.assertThrows(InvalidParameterException.class, () -> PublicTokenGenerator.generate(new String[17]));
        Assertions.assertThrows(InvalidParameterException.class, () -> PublicTokenGenerator.generate("a".repeat(1025)));
        Assertions.assertThrows(InvalidParameterException.class, () -> PublicTokenGenerator.generate("\uD800"));
    }

    /** 同参数多线程调用仍各自生成新的随机主体，无全局可变混入配置。 */
    @Test
    void test_concurrentCallsWithSameInputsProduceDistinctValidBodies() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var results = new ArrayList<Future<String>>();
            for (int index = 0; index < 1000; index++) {
                results.add(executor.submit(() -> PublicTokenGenerator.generate("US", "40001")));
            }
            var tokens = new HashSet<String>();
            for (Future<String> result : results) {
                String token = result.get();
                Assertions.assertTrue(PublicTokenGenerator.isValid(token));
                Assertions.assertTrue(tokens.add(token));
            }
        }
    }
}
