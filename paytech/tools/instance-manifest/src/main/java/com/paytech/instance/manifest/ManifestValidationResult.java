package com.paytech.instance.manifest;

import java.util.List;

/**
 * 清单校验结果。
 *
 * @param violations 校验失败；通过时为空
 * @param manifest 通过时的清单模型；未通过时为 null
 * @author Claude
 */
public record ManifestValidationResult(List<ManifestViolation> violations, InstanceManifest manifest) {

    /**
     * 是否通过。
     *
     * @return 无失败返回 true
     */
    public boolean isValid() {
        return violations.isEmpty();
    }
}
