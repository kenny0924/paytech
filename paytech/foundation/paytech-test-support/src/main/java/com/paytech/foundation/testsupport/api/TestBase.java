package com.paytech.foundation.testsupport.api;

import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Spring 测试的公共清理入口，宿主负责应用启动、两级初始化及清理前的异步停止与调用检查。
 * 实现 Clear 的测试 Bean 自动加入清理列表；只使用 AfterEach，不参与 BeforeEach。
 */
public abstract class TestBase {

    @Autowired
    private List<Clear> clears;

    /** 子类后置检查结束后清理全部组件；某项失败仍尝试其余项并汇总错误。 */
    @AfterEach
    protected final void clearAfterEach() {
        checkClearAllowed();
        assertAll("清理全部测试状态", clears.stream().<Executable>map(clear -> clear::clear));
    }

    /** 异步宿主可在此拒绝尚不能安全清理的环境；同步测试无需额外处理。 */
    protected void checkClearAllowed() {
    }
}
