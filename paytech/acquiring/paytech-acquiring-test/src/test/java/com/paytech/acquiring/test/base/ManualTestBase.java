package com.paytech.acquiring.test.base;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.paytech.acquiring.AcquiringApplication;

import tools.jackson.databind.ObjectMapper;

/** 手工场景测试统一入口：显式 manual Profile，仅连接仓库约定的本地基础设施。 */
@ActiveProfiles("manual")
@AutoConfigureRestTestClient
@SpringBootTest(
    classes = AcquiringApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "server.port=0"
)
public abstract class ManualTestBase {

    @Autowired
    protected RestTestClient restTestClient;

    @Autowired
    protected ObjectMapper objectMapper;
}
