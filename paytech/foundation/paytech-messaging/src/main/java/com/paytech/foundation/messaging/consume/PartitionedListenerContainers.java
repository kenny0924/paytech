package com.paytech.foundation.messaging.consume;

import java.util.List;

import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.context.SmartLifecycle;

/**
 * 本服务全部分区监听容器的统一生命周期；在拓扑声明之后启动，应用关闭时先于连接停止。
 *
 * @author Claude
 */
public final class PartitionedListenerContainers implements SmartLifecycle {

    private final List<SimpleMessageListenerContainer> containers;

    private volatile boolean running;

    /**
     * 创建容器集合。
     *
     * @param containers 已创建、未启动的容器
     */
    public PartitionedListenerContainers(List<SimpleMessageListenerContainer> containers) {
        this.containers = List.copyOf(containers);
    }

    /**
     * 启动全部容器。
     */
    @Override
    public void start() {
        containers.forEach(SimpleMessageListenerContainer::start);
        running = true;
    }

    /**
     * 停止全部容器，等待在途消息处理结束。
     */
    @Override
    public void stop() {
        containers.forEach(SimpleMessageListenerContainer::stop);
        running = false;
    }

    /**
     * 是否已启动。
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 已创建的容器，按发布方与分区序号排列。
     *
     * @return 不可变列表
     */
    public List<SimpleMessageListenerContainer> containers() {
        return containers;
    }

    /**
     * 停止全部容器并释放其消费者资源。
     */
    public void destroy() {
        containers.forEach(SimpleMessageListenerContainer::destroy);
        running = false;
    }
}
