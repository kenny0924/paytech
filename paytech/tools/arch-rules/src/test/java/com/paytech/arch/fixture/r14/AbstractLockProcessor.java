package com.paytech.arch.fixture.r14;

/** R14 fixture：模拟 Foundation 资源锁模板，间接继承同步命令模板。 */
public abstract class AbstractLockProcessor<Request, Response> extends AbstractProcessor<Request, Response> {

    @Override
    protected Response processRequest(Request request) {
        return processWithinLock(request);
    }

    /** 锁内处理。 */
    protected abstract Response processWithinLock(Request request);
}
