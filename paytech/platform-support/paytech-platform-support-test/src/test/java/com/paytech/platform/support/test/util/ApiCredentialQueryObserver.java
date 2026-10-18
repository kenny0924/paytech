package com.paytech.platform.support.test.util;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.foundation.testsupport.api.Clear;

/** 只观察真实 SQL 及事务；并发用例可在第一次读取后暂停，不替换查询结果。 */
@TestComponent
@Intercepts(@Signature(type = Executor.class, method = "query",
    args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}))
public class ApiCredentialQueryObserver implements Interceptor, Clear {

    private static final String CANDIDATES = "com.paytech.platform.support.accesscontrol.core.dal.mapper.ApiCredentialMapper.selectCandidates";
    private static final String SCOPES = "com.paytech.platform.support.accesscontrol.core.dal.mapper.ApiCredentialScopeMapper.selectEffectiveScopes";
    private final List<Connection> connections = new ArrayList<>();
    private volatile QueryBarrier barrier;

    /** 检查实际事务属性；全部 SQL 都继续交给原始 Executor 和真实数据库。 */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        String statement = ((MappedStatement) invocation.getArgs()[0]).getId();
        if (!CANDIDATES.equals(statement) && !SCOPES.equals(statement)) {
            return invocation.proceed();
        }
        Connection connection = ((Executor) invocation.getTarget()).getTransaction().getConnection();
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive(), "认证查询必须处于本地事务");
        assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly(), "认证必须使用只读事务");
        assertTrue(connection.isReadOnly(), "JDBC 连接必须只读");
        assertEquals(Connection.TRANSACTION_REPEATABLE_READ, connection.getTransactionIsolation());
        synchronized (connections) {
            connections.add(connection);
        }
        Object result = invocation.proceed();
        QueryBarrier current = barrier;
        if (CANDIDATES.equals(statement) && current != null) {
            current.candidateRead.countDown();
            assertTrue(current.resume.await(1, TimeUnit.SECONDS), "并发测试未及时完成事务外变更");
        }
        return result;
    }

    /** 标记单次 HTTP 请求之前的观察位置，不清除其他调用证据。 */
    public int position() {
        synchronized (connections) {
            return connections.size();
        }
    }

    /** 成功认证必须有两次读取且使用同一事务连接。 */
    public void assertSnapshotSince(int position) {
        synchronized (connections) {
            var reads = connections.subList(position, connections.size());
            assertEquals(2, reads.size(), "成功认证应读取候选和当前授权");
            assertSame(reads.getFirst(), reads.getLast(), "两次查询必须共享同一连接快照");
        }
    }

    /** 初始化用例时验证上例观察和同步门已经清除。 */
    public void assertEmpty() {
        assertEquals(0, position(), "上例 SQL 观察未清理");
        assertNull(barrier, "上例同步门未清理");
    }

    /** 只为明确的并发场景启用一次性同步门。 */
    public QueryBarrier pauseAfterCandidate() {
        assertNull(barrier, "不允许嵌套同步门");
        barrier = new QueryBarrier();
        return barrier;
    }

    /** HTTP 完成后回收观察记录，释放异常路径仍未打开的门。 */
    @Override
    public void clear() {
        if (barrier != null) {
            barrier.close();
            barrier = null;
        }
        synchronized (connections) {
            connections.clear();
        }
    }

    /** 有界门禁只控制测试时序，不持有数据库事务或生产对象。 */
    public static final class QueryBarrier implements AutoCloseable {
        private final CountDownLatch candidateRead = new CountDownLatch(1);
        private final CountDownLatch resume = new CountDownLatch(1);

        /** 等待真实候选 SELECT 返回，再从其他连接提交新事实。 */
        public void awaitCandidate() throws InterruptedException {
            assertTrue(candidateRead.await(5, TimeUnit.SECONDS), "未观察到真实候选查询");
        }

        /** 即使用例断言失败也允许请求完成，之后才能清理数据。 */
        @Override
        public void close() {
            resume.countDown();
        }
    }
}
