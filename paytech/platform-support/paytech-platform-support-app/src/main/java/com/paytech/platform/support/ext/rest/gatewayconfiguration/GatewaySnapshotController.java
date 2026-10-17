package com.paytech.platform.support.ext.rest.gatewayconfiguration;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.accesscontrol.service.processor.QueryGatewaySnapshotProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.request.QueryGatewaySnapshotRequest;
import com.paytech.platform.support.api.gatewayconfiguration.GatewaySnapshotQueryApi;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayPublicSnapshotResponseBody;

import lombok.AllArgsConstructor;

/**
 * PLT-I20 Gateway Snapshot HTTP 适配器。
 */
@RestController
@AllArgsConstructor
public class GatewaySnapshotController implements GatewaySnapshotQueryApi {

    private final GatewaySnapshotConverter converter;
    private final QueryGatewaySnapshotProcessor processor;

    /** 查询完整当前快照，并仅在协议边界转换响应。 */
    @Override
    public GatewayPublicSnapshotResponseBody queryPublicSnapshot() {
        return converter.convertResponse(processor.process(new QueryGatewaySnapshotRequest()));
    }
}
