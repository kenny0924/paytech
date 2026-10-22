package com.paytech.platform.support.test.dataquery;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.platform.support.dataquery.integration.DataSourcePoolRegistry;

import lombok.RequiredArgsConstructor;

/** 每例结束关闭本例建立的全部被查库连接池；数据源表由 PlatformDataCleaner 整表回收，连接池不能跨例残留。 */
@TestComponent
@RequiredArgsConstructor
public class DataQueryPoolCleaner implements Clear {

    private final DataSourcePoolRegistry pools;

    /** 关闭并清空注册表中的连接池。 */
    @Override
    public void clear() {
        pools.destroy();
    }
}
