package com.paytech.platform.support.referencedata.client.test.base;

import java.io.IOException;
import java.net.URI;

import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequest;
import org.springframework.cloud.client.loadbalancer.LoadBalancerUriTools;
import org.springframework.cloud.client.loadbalancer.Request;

/**
 * 测试用服务发现：只把一个服务名解析为本机 Mock 服务实例，其他服务名没有实例（真实 LoadBalancerInterceptor 据此失败）。
 */
public final class MockServerLoadBalancerClient implements LoadBalancerClient {

    private final String serviceId;

    private final URI mockServer;

    /**
     * 绑定服务名与 Mock 服务地址
     *
     * @param serviceId 可解析的服务名
     * @param mockServerBaseUrl Mock 服务根地址
     */
    public MockServerLoadBalancerClient(String serviceId, String mockServerBaseUrl) {
        this.serviceId = serviceId;
        this.mockServer = URI.create(mockServerBaseUrl);
    }

    /** 只有绑定的服务名有实例。 */
    @Override
    public ServiceInstance choose(String requestedServiceId) {
        if (!serviceId.equals(requestedServiceId)) {
            return null;
        }
        return new DefaultServiceInstance(serviceId + "-1", serviceId, mockServer.getHost(), mockServer.getPort(), false);
    }

    /** 忽略负载均衡上下文。 */
    @Override
    public <T> ServiceInstance choose(String requestedServiceId, Request<T> request) {
        return choose(requestedServiceId);
    }

    /** 选择实例后执行。 */
    @Override
    public <T> T execute(String requestedServiceId, LoadBalancerRequest<T> request) throws IOException {
        ServiceInstance instance = choose(requestedServiceId);
        if (instance == null) {
            throw new IllegalStateException("No instances available for " + requestedServiceId);
        }
        return execute(requestedServiceId, instance, request);
    }

    /** 在给定实例上执行。 */
    @Override
    public <T> T execute(String requestedServiceId, ServiceInstance instance, LoadBalancerRequest<T> request)
        throws IOException {
        try {
            return request.apply(instance);
        } catch (IOException | RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IOException(failure);
        }
    }

    /** 用实例地址替换服务名主机。 */
    @Override
    public URI reconstructURI(ServiceInstance instance, URI original) {
        return LoadBalancerUriTools.reconstructURI(instance, original);
    }
}
