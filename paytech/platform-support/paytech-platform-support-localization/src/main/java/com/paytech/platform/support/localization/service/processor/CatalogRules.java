package com.paytech.platform.support.localization.service.processor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.platform.support.localization.service.processor.request.ApplicationCommand;

/** 应用新增与修改共用的输入规则。 */
final class CatalogRules {

    private CatalogRules() {
    }

    /**
     * 必须覆盖语言不能重复。
     *
     * @param request 应用命令
     */
    static void requireDistinctLocales(ApplicationCommand request) {
        Set<String> seen = new HashSet<>();
        List<String> duplicated = request.getRequiredLocales().stream().filter(code -> !seen.add(code)).distinct()
            .toList();
        if (!duplicated.isEmpty()) {
            throw new BizCheckFailException("必须覆盖语言重复: " + String.join("、", duplicated));
        }
    }

    /**
     * 应用引用的全部语言：默认语言与必须覆盖语言。
     *
     * @param request 应用命令
     * @return 语言代码
     */
    static List<String> referencedLocales(ApplicationCommand request) {
        List<String> locales = new ArrayList<>(request.getRequiredLocales());
        locales.add(request.getDefaultLocale());
        return locales;
    }
}
