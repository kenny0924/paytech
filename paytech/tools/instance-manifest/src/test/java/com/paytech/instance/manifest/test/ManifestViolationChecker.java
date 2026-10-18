package com.paytech.instance.manifest.test;

import com.paytech.instance.manifest.ManifestValidationResult;
import com.paytech.instance.manifest.ManifestViolation;
import com.paytech.instance.manifest.ManifestViolationCode;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;

/**
 * 清单校验结果断言：按 {@code 路径 + 类别} 精确比对全部失败。
 *
 * @author Claude
 */
public final class ManifestViolationChecker {

    private final ManifestValidationResult result;

    private ManifestViolationChecker(ManifestValidationResult result) {
        this.result = result;
    }

    /**
     * 包装校验结果。
     *
     * @param result 结果
     * @return 断言器
     */
    public static ManifestViolationChecker of(ManifestValidationResult result) {
        return new ManifestViolationChecker(result);
    }

    /**
     * 断言通过。
     *
     * @return 断言器
     */
    public ManifestViolationChecker valid() {
        Assertions.assertTrue(result.isValid(), () -> "预期通过，实际:\n" + describe(result.violations()));
        Assertions.assertNotNull(result.manifest());
        return this;
    }

    /**
     * 断言失败（可重复）恰为给定的 {@code 路径 [类别]}。
     *
     * @param expected 形如 {@code services.ledger.port=TYPE_MISMATCH}
     * @return 断言器
     */
    public ManifestViolationChecker exactly(String... expected) {
        Assertions.assertFalse(result.isValid(), "预期校验失败");
        Assertions.assertNull(result.manifest(), "未通过时不应返回清单模型");
        List<String> actual = result.violations().stream()
            .map(violation -> violation.path() + "=" + violation.code())
            .sorted()
            .toList();
        Assertions.assertEquals(Stream.of(expected).sorted().toList(), actual, () -> describe(result.violations()));
        return this;
    }

    /**
     * 断言某类失败的说明包含片段。
     *
     * @param code 类别
     * @param fragment 片段
     * @return 断言器
     */
    public ManifestViolationChecker message(ManifestViolationCode code, String fragment) {
        boolean found = result.violations().stream()
            .anyMatch(violation -> violation.code() == code && violation.message().contains(fragment));
        Assertions.assertTrue(found, () -> "未找到 " + code + " 含 " + fragment + "，实际:\n" + describe(result.violations()));
        return this;
    }

    private static String describe(List<ManifestViolation> violations) {
        return violations.stream().map(ManifestViolation::toString).collect(Collectors.joining("\n"));
    }
}
