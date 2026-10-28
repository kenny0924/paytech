package com.paytech.platform.support.accesscontrol.service.processor;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsPersonnelService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ListOperationsAccountLabelsRequest;

import lombok.AllArgsConstructor;

/**
 * 批量查询 Operations 账号显示名；只读，不开事务。
 * <li>只接受 operations-console-bff：显示名供运营页面把操作人账号 ID 展示为可读名称，没有其他消费者。</li>
 */
@Component
@AllArgsConstructor
public class ListOperationsAccountLabelsProcessor
    extends AbstractProcessor<ListOperationsAccountLabelsRequest, Map<Long, String>> {

    /** 唯一获准的调用方服务名。 */
    static final String OPERATIONS_CONSOLE_BFF = "operations-console-bff";

    private final OperationsPersonnelService personnel;

    @Override
    protected Map<Long, String> processRequest(ListOperationsAccountLabelsRequest request) {
        if (!OPERATIONS_CONSOLE_BFF.equals(request.getCallerService())) {
            throw new BizCheckFailException("调用方未获准查询运营账号显示名: " + request.getCallerService());
        }
        return personnel.labels(request.getAccountIds());
    }
}
