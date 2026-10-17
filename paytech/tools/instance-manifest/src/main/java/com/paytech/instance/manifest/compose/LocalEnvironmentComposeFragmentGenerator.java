package com.paytech.instance.manifest.compose;

import java.nio.file.Path;

/**
 * 按实例清单生成 {@code local-environment} 的 Compose 片段。只返回文本，不写入 {@code local-environment/}。
 *
 * @author Claude
 */
public interface LocalEnvironmentComposeFragmentGenerator {

    /**
     * 校验清单并生成片段。
     *
     * @param manifestFile 清单 YAML
     * @return 生成结果；清单未通过校验时携带失败列表
     */
    ComposeFragmentResult generate(Path manifestFile);
}
