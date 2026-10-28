package com.paytech.platform.support.ext.rest.authentication;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.accesscontrol.service.processor.ListOperationsAccountLabelsProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.request.ListOperationsAccountLabelsRequest;
import com.paytech.platform.support.api.operations.OperationsAccountLabelQueryApi;
import com.paytech.platform.support.api.operations.response.OperationsAccountLabelListResponseBody;
import com.paytech.platform.support.api.operations.response.OperationsAccountLabelResponseBody;
import com.paytech.platform.support.ext.rest.internalauth.InternalCallerResolver;

import lombok.AllArgsConstructor;

/**
 * Operations 账号显示名查询的协议适配；不开事务。
 * <li>调用方经 {@link InternalCallerResolver} 确定（携带已验证内部 Token 时以 Token sub 为准），准入校验在 Processor。</li>
 */
@RestController
@AllArgsConstructor
public class OperationsAccountLabelController implements OperationsAccountLabelQueryApi {

    private final InternalCallerResolver internalCallers;

    private final ListOperationsAccountLabelsProcessor processor;

    /** 解析调用方与账号 ID 后委托查询。 */
    @Override
    public OperationsAccountLabelListResponseBody listLabels(String callerService, String accountIds) {
        Map<Long, String> labels = processor.process(
            new ListOperationsAccountLabelsRequest(internalCallers.resolve(callerService), accountIds(accountIds)));
        OperationsAccountLabelListResponseBody body = new OperationsAccountLabelListResponseBody();
        body.setItems(labels.entrySet().stream().map(entry -> item(entry.getKey(), entry.getValue())).toList());
        return body;
    }

    /**
     * 逗号分隔的十进制账号 ID；为空时返回空列表，由 Processor 统一拒绝。
     *
     * @param accountIds 原始 Query 值
     * @return 账号 ID，保持原顺序
     * @throws InvalidParameterException 含空段或非数字
     */
    static List<Long> accountIds(String accountIds) {
        if (accountIds == null || accountIds.isBlank()) {
            return List.of();
        }
        try {
            return Arrays.stream(accountIds.split(",", -1)).map(id -> Long.valueOf(id.strip())).toList();
        } catch (NumberFormatException exception) {
            throw new InvalidParameterException("accountIds 必须是逗号分隔的十进制账号 ID");
        }
    }

    /** 显示名响应元素；账号 ID 按字符串输出。 */
    static OperationsAccountLabelResponseBody item(Long accountId, String label) {
        OperationsAccountLabelResponseBody item = new OperationsAccountLabelResponseBody();
        item.setAccountId(Long.toString(accountId));
        item.setLabel(label);
        return item;
    }
}
