package com.paytech.platform.support.referencedata.client.internal;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerInterceptor;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.util.ClassUtils;

/**
 * 未配置 base-url 时的服务发现目标：用宿主的 LoadBalancerClient 把 {@code http://<serviceId>/...} 解析为实例。
 * <li>只在 spring-cloud-commons 位于 classpath 时加载 LoadBalancer 类型；缺少类或 Bean 时启动失败并说明原因。</li>
 */
public final class LoadBalancedPlatformTarget {

    private static final String LOAD_BALANCER_CLIENT = "org.springframework.cloud.client.loadbalancer.LoadBalancerClient";

    private LoadBalancedPlatformTarget() {
    }

    /**
     * 创建按服务名选择实例的拦截器
     *
     * @param beanFactory 宿主容器
     * @param serviceId 服务发现名（用于错误说明）
     * @return LoadBalancer 拦截器
     * @throws IllegalStateException classpath 或容器中没有 LoadBalancerClient
     */
    public static ClientHttpRequestInterceptor interceptor(BeanFactory beanFactory, String serviceId) {
        if (!ClassUtils.isPresent(LOAD_BALANCER_CLIENT, LoadBalancedPlatformTarget.class.getClassLoader())) {
            throw missingLoadBalancer(serviceId);
        }
        return Discovery.interceptor(beanFactory, serviceId);
    }

    /** 未配置直连地址又无法服务发现。 */
    private static IllegalStateException missingLoadBalancer(String serviceId) {
        return new IllegalStateException("参考数据客户端未配置 platform-support.reference-data.client.base-url，需要经服务发现访问 "
            + serviceId + "，但宿主没有 LoadBalancerClient：请引入 spring-cloud-starter-loadbalancer 与服务发现，或配置 base-url 直连");
    }

    /** 引用 LoadBalancer 类型的部分，只在类存在时加载。 */
    private static final class Discovery {

        /** 取宿主 LoadBalancerClient 并包装为拦截器。 */
        private static ClientHttpRequestInterceptor interceptor(BeanFactory beanFactory, String serviceId) {
            LoadBalancerClient client = beanFactory.getBeanProvider(LoadBalancerClient.class).getIfAvailable();
            if (client == null) {
                throw missingLoadBalancer(serviceId);
            }
            return new LoadBalancerInterceptor(client);
        }
    }
}
