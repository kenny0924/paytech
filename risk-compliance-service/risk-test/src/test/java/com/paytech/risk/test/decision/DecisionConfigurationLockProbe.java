package com.paytech.risk.test.decision;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/** 仅暂停已经完成的真实锁查询，不替换 SQL、业务返回或事务，用于安排竞争顺序。 */
@Intercepts(@Signature(type = Executor.class, method = "query",
    args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}))
public final class DecisionConfigurationLockProbe implements Interceptor {
    private final String mapperMethod;
    private volatile Gate gate;

    /** 两个真实消费者共用锁观察器，精确限定各自的配置根查询。 */
    public DecisionConfigurationLockProbe(String mapperMethod) {
        this.mapperMethod = mapperMethod;
    }

    /** 对本用例唯一代码安排先持有的锁；其他查询完全直通。 */
    public Gate arm(String code, String firstLock) {
        gate = new Gate(code, firstLock);
        return gate;
    }

    /** 先执行真实 MySQL 查询，再在事务仍持锁时通知测试线程。 */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Gate current = gate;
        var statement = (MappedStatement) invocation.getArgs()[0];
        Object parameters = invocation.getArgs()[1];
        if (current == null || !statement.getId().contains(mapperMethod)
            || !(parameters instanceof Map<?, ?> values) || !current.code.equals(values.get("code"))) {
            return invocation.proceed();
        }
        String lock = String.valueOf(values.get("lock"));
        if (!lock.equals("WRITE") && !lock.equals("SHARE")) return invocation.proceed();
        if (!lock.equals(current.firstLock)) current.competitorEntered.countDown();
        Object result = invocation.proceed();
        if (lock.equals(current.firstLock)) {
            current.acquired.countDown();
            if (!current.release.await(5, TimeUnit.SECONDS)) throw new AssertionError("测试未及时释放配置根锁协调器");
        }
        return result;
    }

    /** 只持有测试同步信号，不拥有连接、事务或业务状态。 */
    public static final class Gate implements AutoCloseable {
        private final String code;
        private final String firstLock;
        private final CountDownLatch acquired = new CountDownLatch(1);
        private final CountDownLatch competitorEntered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        /** 绑定唯一测试资源及需要先获取的数据库锁。 */
        private Gate(String code, String firstLock) { this.code = code; this.firstLock = firstLock; }

        /** 等待首个公开请求已经实际取得 MySQL 锁。 */
        public boolean awaitAcquired() throws InterruptedException { return acquired.await(3, TimeUnit.SECONDS); }

        /** 等待另一个公开请求到达同码竞争 SQL，不使用固定睡眠。 */
        public boolean awaitCompetitor() throws InterruptedException { return competitorEntered.await(3, TimeUnit.SECONDS); }

        /** 允许持锁请求继续完成生产事务。 */
        @Override public void close() { release.countDown(); }
    }
}
