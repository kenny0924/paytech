package com.paytech.operations.console.bff.ext.rest;

import java.util.Set;

import com.paytech.foundation.common.error.InvalidParameterException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 商务列表查询的 Query 白名单：未知键、空值拒绝；只有声明为可重复的键（status）允许多值
 * <li>RiskListController.requireQueryKeys 一律拒绝重复键；commercial 合同与版本列表的 status 是多值筛选，因此单独提供。</li>
 * <li>多值上限 MAX_REPEATED_VALUES，覆盖合同与版本的全部状态个数，防止重复参数放大上游请求。</li>
 *
 * @author Claude
 */
final class CommercialQueryParameters {
    /** 可重复键的取值个数上限。 */
    static final int MAX_REPEATED_VALUES = 7;

    /** 只承载静态方法，禁止创建实例。 */
    private CommercialQueryParameters() {
    }

    /**
     * 校验 Query 键
     *
     * @param request 当前请求
     * @param allowedKeys 允许的键
     * @param repeatableKeys 允许多值的键（须同时在 allowedKeys 中）
     */
    static void require(HttpServletRequest request, Set<String> allowedKeys, Set<String> repeatableKeys) {
        for (var entry : request.getParameterMap().entrySet()) {
            String[] values = entry.getValue();
            int limit = repeatableKeys.contains(entry.getKey()) ? MAX_REPEATED_VALUES : 1;
            if (!allowedKeys.contains(entry.getKey()) || values.length > limit) {
                throw new InvalidParameterException("请求包含不支持或重复的 Query 参数");
            }
            for (String value : values) {
                if (value.isEmpty()) {
                    throw new InvalidParameterException("请求包含为空的 Query 参数");
                }
            }
        }
    }
}
