package com.paytech.operations.console.bff.service.processor;

import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.paytech.operations.console.bff.integration.OperationsAccountLabelClient;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 页面操作人账号 ID 到显示名的批量补全：一页内去重后查询 Platform，失败降级为不补全
 * <li>只查询十进制账号 ID；null 与 system 等非账号操作人跳过，由页面原样展示。</li>
 * <li>Platform 失败、超时或回执无效只记录告警（不含账号 ID），返回空结果，不影响所在列表。</li>
 */
@Slf4j
@Component
@AllArgsConstructor
public class OperatorLabelResolver {
    private static final String ACCOUNT_ID = "[1-9][0-9]{0,18}";

    private final OperationsAccountLabelClient client;

    /**
     * 查询一组操作人的显示名
     *
     * @param operators 本页操作人，允许 null 与非账号值
     * @return 账号 ID 到显示名；无可查询账号或查询失败时为空
     */
    public Map<String, String> resolve(Stream<String> operators) {
        var accountIds = operators.filter(Objects::nonNull).filter(OperatorLabelResolver::isAccountId)
            .collect(Collectors.toCollection(TreeSet::new));
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        try {
            return client.findLabels(accountIds);
        } catch (RuntimeException failure) {
            log.warn("运营账号显示名查询失败，操作人显示名留空, accounts={}, cause={}", accountIds.size(),
                failure.getClass().getSimpleName());
            return Map.of();
        }
    }

    /**
     * 操作人对应的显示名
     *
     * @param labels {@link #resolve} 的结果
     * @param operator 操作人，允许 null
     * @return 显示名；操作人为空或未查到时为 null
     */
    public static String labelOf(Map<String, String> labels, String operator) {
        return operator == null ? null : labels.get(operator);
    }

    /** 十进制正整数且不超出 Platform 账号 ID 的 long 范围。 */
    static boolean isAccountId(String operator) {
        if (!operator.matches(ACCOUNT_ID)) {
            return false;
        }
        try {
            Long.parseLong(operator);
            return true;
        } catch (NumberFormatException overflow) {
            return false;
        }
    }
}
