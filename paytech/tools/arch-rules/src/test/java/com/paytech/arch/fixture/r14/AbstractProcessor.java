package com.paytech.arch.fixture.r14;

/** R14 fixture：模拟 Foundation 同步命令模板。 */
public abstract class AbstractProcessor<Request, Response> {

    /** 执行命令。 */
    protected abstract Response processRequest(Request request);
}
