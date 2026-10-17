package com.paytech.instance.manifest.compose;

import com.paytech.instance.manifest.ManifestViolation;

import java.util.List;

/**
 * Compose 片段生成结果。
 *
 * @param fragment 生成的 YAML 文本；清单未通过校验时为 null
 * @param violations 清单校验失败；成功时为空
 * @author Claude
 */
public record ComposeFragmentResult(String fragment, List<ManifestViolation> violations) {

    /**
     * 是否生成成功。
     *
     * @return 成功返回 true
     */
    public boolean isGenerated() {
        return fragment != null;
    }
}
