package com.paytech.instance.manifest;

import java.nio.file.Path;

/**
 * 实例清单 Schema v1 校验（design/01 §5）。
 *
 * <p>
 * 失败以 {@link ManifestViolation} 列表返回，不抛出异常作为控制流。
 *
 * @author Claude
 */
public interface InstanceManifestValidator {

    /**
     * 校验清单文件。
     *
     * @param manifestFile 清单 YAML
     * @return 校验结果
     */
    ManifestValidationResult validate(Path manifestFile);

    /**
     * 校验清单文本。
     *
     * @param sourceName 来源名（用于说明）
     * @param content 清单 YAML 文本
     * @return 校验结果
     */
    ManifestValidationResult validateContent(String sourceName, String content);
}
