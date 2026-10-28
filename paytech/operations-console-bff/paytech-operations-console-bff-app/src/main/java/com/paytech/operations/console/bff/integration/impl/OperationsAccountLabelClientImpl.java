package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.require;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import org.springframework.stereotype.Component;

import com.paytech.operations.console.bff.integration.OperationsAccountLabelClient;
import com.paytech.platform.support.api.operations.OperationsAccountLabelQueryApi;
import com.paytech.platform.support.api.operations.response.OperationsAccountLabelResponseBody;

import lombok.AllArgsConstructor;

/**
 * Platform 运营账号显示名的 HTTP 转发：按 Platform 单次 50 个的上限分批，每批调用一次，不重试
 * <li>调用方显式为 operations-console-bff（Platform 携带已验证内部 Token 时以 Token sub 为准）。</li>
 * <li>回执中的账号须属于本批请求、显示名非空且不重复；不符按上游无效响应处理，由调用方决定是否降级。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class OperationsAccountLabelClientImpl implements OperationsAccountLabelClient {
    /** 调用方服务名。 */
    static final String CALLER_SERVICE = "operations-console-bff";

    /** Platform 单次查询的账号数上限。 */
    static final int BATCH_SIZE = 50;

    private final OperationsAccountLabelQueryApi api;

    /** 分批查询并合并显示名。 */
    @Override
    public Map<String, String> findLabels(SortedSet<String> accountIds) {
        Map<String, String> labels = new HashMap<>();
        List<String> ids = new ArrayList<>(accountIds);
        for (int from = 0; from < ids.size(); from += BATCH_SIZE) {
            List<String> batch = ids.subList(from, Math.min(from + BATCH_SIZE, ids.size()));
            var response = api.listLabels(CALLER_SERVICE, String.join(",", batch));
            require(response != null && response.getItems() != null);
            for (OperationsAccountLabelResponseBody item : response.getItems()) {
                require(item != null && batch.contains(item.getAccountId()) && item.getLabel() != null
                    && !item.getLabel().isBlank() && !labels.containsKey(item.getAccountId()));
                labels.put(item.getAccountId(), item.getLabel());
            }
        }
        return labels;
    }
}
