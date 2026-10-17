package com.paytech.platform.support.delivery.integration.provider.ses;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/**
 * 按区域复用的 SES 客户端
 * <li>账号可分属不同区域；同一区域共用一个客户端，终端覆盖与超时取环境配置</li>
 * <li>客户端不持有凭据，密钥按请求覆盖；容器关闭时统一关闭</li>
 */
@RequiredArgsConstructor
public class SesClients implements AutoCloseable {

    private final SesEmailProperties properties;

    private final Map<String, SesV2Client> clients = new ConcurrentHashMap<>();

    /** 区域对应的客户端，首次使用时创建；构造不发起网络请求。 */
    public SesV2Client client(String region) {
        return clients.computeIfAbsent(region, r -> SesEmailConfiguration.client(properties, r));
    }

    /** 关闭全部已创建的客户端。 */
    @Override
    public void close() {
        clients.values().forEach(SesV2Client::close);
        clients.clear();
    }
}
