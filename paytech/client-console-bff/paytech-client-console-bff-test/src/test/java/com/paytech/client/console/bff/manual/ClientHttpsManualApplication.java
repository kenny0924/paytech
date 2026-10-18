package com.paytech.client.console.bff.manual;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.context.support.TestPropertySourceUtils;
import com.paytech.client.console.bff.ClientConsoleBffApplication;

/** 显式 HTTPS 联调入口，启动真实 BFF 并连接本轮 Platform 私网端口。 */
public final class ClientHttpsManualApplication {
    /** 使用原 auto 的 loopback 限制，不扩展生产直连配置。 */
    public static void main(String[] args) throws Exception {
        var env = new StandardEnvironment();
        String run = env.getRequiredProperty("CLIENT_HTTPS_RUN_ID");
        if (!run.matches("[a-f0-9-]{36}")) { throw new IllegalArgumentException("缺少本机联调身份"); }
        var app = new SpringApplication(ClientConsoleBffApplication.class);
        app.setAdditionalProfiles("auto");
        app.addInitializers(context -> TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
            "client.platform-test-base-url=" + env.getRequiredProperty("CLIENT_HTTPS_PLATFORM_URL") + "/platform-support",
            "client.console-origin=" + env.getRequiredProperty("CLIENT_HTTPS_ORIGIN")));
        var context = app.run(args);
        Files.writeString(Path.of(env.getRequiredProperty("CLIENT_HTTPS_STATE_DIR"), "bff.json"),
            "{\"port\":" + ((WebServerApplicationContext)context).getWebServer().getPort() + "}");
    }
}
