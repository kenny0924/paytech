package com.paytech.channel.connector.protocol.ext.outbound;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.enums.ChannelType;
import com.paytech.channel.connector.protocol.core.configuration.ExternalApiExecutionProperties;

/**
 * 按渠道类型分开的两个执行舱的并发许可（technique 第 6.3 节）
 * <li>一次标准 API 执行在第一个原子节点开始前取得所属类型的一个许可，结束后归还；拿不到立即失败、不排队，避免服务渠道的慢调用占满请求线程。</li>
 * <li>每个执行舱另有独立的 JDK HttpClient 实例（见 {@link ChannelHttpClient}），连接池与选择器线程互不共享。</li>
 * <li>上限在启动时读取，修改需重启。</li>
 */
@Component
public class ChannelExecutionCompartments {

    private final Map<ChannelType, Semaphore> permits = new EnumMap<>(ChannelType.class);

    /** 按配置创建两个执行舱的许可。 */
    public ChannelExecutionCompartments(ExternalApiExecutionProperties properties) {
        permits.put(ChannelType.PAYMENT, new Semaphore(properties.getPaymentMaxConcurrency()));
        permits.put(ChannelType.SERVICE, new Semaphore(properties.getServiceMaxConcurrency()));
    }

    /**
     * 立即尝试进入执行舱
     *
     * @param type 渠道类型
     * @return 取得的许可，用完必须关闭归还；没有空闲许可时返回 null
     */
    public Permit tryEnter(ChannelType type) {
        Semaphore semaphore = permits.get(type);
        return semaphore.tryAcquire() ? new Permit(semaphore) : null;
    }

    /** 当前空闲许可数，只用于观测。 */
    public int available(ChannelType type) {
        return permits.get(type).availablePermits();
    }

    /** 一个执行舱许可；关闭时只归还一次。 */
    public static final class Permit implements AutoCloseable {
        private final Semaphore semaphore;
        private final AtomicBoolean released = new AtomicBoolean();

        /** 已取得的许可。 */
        Permit(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        /** 归还许可。 */
        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                semaphore.release();
            }
        }
    }
}
