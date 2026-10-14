import com.paytech.platform.support.PlatformSupportTestApplication;
import com.paytech.platform.support.test.builder.OperationsCatalog;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.configuration.LocalMockEnvironmentInitializer;
import com.paytech.platform.support.test.mocker.external.ChannelAccountVaultMocker;
import com.paytech.platform.support.test.mocker.local.OperationsMocker;
import com.paytech.platform.support.test.util.TestClock;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;

/**
 * PayerMax 接入本机联调：真实 Platform（测试模块装配提供开发签名 Key 与 Vault Mock HTTP），只连接独立库 mor_platform_external_api，
 * 去掉测试固定时钟（requestTime 必须是真实时间）。
 *
 * <p>Vault Mock 登记 PAYERMAX 渠道账号（CHANNEL_EXECUTION）：
 * mock 账号的密钥来自本机合成 PayerMax 生成的 E2E_RUNTIME/payermax-mock-keys.json；
 * 若设置 PAYERMAX_UAT_ACCOUNT_FILE（仓库外 0600 文件，含 appId、merchantNo、keyVersion、merchantPrivateKey、payermaxPublicKey），
 * 另登记 uat 账号。密钥只进入 Vault Mock 内存，platform.json 只写 Vault Token 与账号身份。
 */
public class PlatformPayerMaxE2e {
    static final JsonMapper JSON = JsonMapper.builder().build();

    public static void main(String[] args) throws Exception {
        Path runtime = Path.of(args[0]).getParent();
        String marker = "payermax-e2e-" + UUID.randomUUID().toString().substring(0, 8);
        var context = new SpringApplicationBuilder(PlatformSupportTestApplication.class)
            .initializers(new LocalMockEnvironmentInitializer(), ctx -> ctx.getEnvironment().getPropertySources().addFirst(
                // 测试框架之外 DynamicPropertyRegistrar 不生效：首次读取时再从 Mock HTTP 服务取实际地址。
                new org.springframework.core.env.PropertySource<Object>("e2eMockHttp") {
                    @Override public Object getProperty(String name) {
                        return "test.mock-http.base-url".equals(name)
                            ? ctx.getBeanFactory().getBean(com.paytech.foundation.testsupport.api.MockHttpServer.class).baseUrl() : null;
                    }
                }), ctx -> ctx.addBeanFactoryPostProcessor(new BeanDefinitionRegistryPostProcessor() {
                @Override public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
                    registry.registerBeanDefinition("e2eRemoveTestClock", new RootBeanDefinition(RemoveTestClock.class));
                }
                @Override public void postProcessBeanFactory(ConfigurableListableBeanFactory factory) { }
            }))
            .run("--spring.profiles.active=mock", "--server.address=127.0.0.1", "--server.port=0",
                "--spring.datasource.url=jdbc:mysql://127.0.0.1:13306/mor_platform_external_api?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                "--redis.key-prefix=platform-support:" + marker, "--spring.cloud.service-registry.auto-registration.enabled=false");
        var jdbc = context.getBean(JdbcTemplate.class);
        if (!"mor_platform_external_api".equals(jdbc.queryForObject("SELECT DATABASE()", String.class))) {
            context.close();
            throw new IllegalStateException("只允许本次独立验证库");
        }
        var operations = context.getBean(OperationsMocker.class);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM t_jwt_signing_key WHERE realm='OPERATIONS' AND status='ACTIVE'", Integer.class) == 0) {
            operations.signingKey("OPERATIONS", OperationsCatalog.OPERATIONS_KID, operations.now().minusSeconds(60));
        }
        var admin = operations.operator(OperatorBuilder.administrator().withoutSession());

        var vault = context.getBean(ChannelAccountVaultMocker.class);
        ObjectNode accounts = JSON.createObjectNode();
        accounts.set("mock", register(vault, marker, "mock", "900000000000000001", "LOCAL",
            JSON.readTree(Files.readString(runtime.resolve("payermax-mock-keys.json")))));
        String uatFile = System.getenv("PAYERMAX_UAT_ACCOUNT_FILE");
        if (uatFile != null && !uatFile.isBlank()) {
            accounts.set("uat", register(vault, marker, "uat", "900000000000000002", "UAT01", JSON.readTree(Files.readString(Path.of(uatFile)))));
        }
        ObjectNode out = JSON.createObjectNode().put("port", ((WebServerApplicationContext) context).getWebServer().getPort())
            .put("marker", marker).put("admin", admin.email()).put("password", OperatorBuilder.PASSWORD);
        out.set("accounts", accounts);
        Files.writeString(Path.of(args[0]), out.toString());
        Files.setPosixFilePermissions(Path.of(args[0]), PosixFilePermissions.fromString("rw-------"));
        Runtime.getRuntime().addShutdownHook(new Thread(context::close));
        Thread.currentThread().join();
    }

    /** 登记一个 CHANNEL_ACCOUNT_CONFIG_V1 账号，返回不含密钥的账号身份与 Vault Token。 */
    static ObjectNode register(ChannelAccountVaultMocker vault, String marker, String name, String accountId, String entityNo, JsonNode material) {
        for (String key : List.of("appId", "merchantNo", "keyVersion", "merchantPrivateKey", "payermaxPublicKey")) {
            if (material.path(key).asString("").isBlank()) throw new IllegalStateException(name + " 账号材料缺少 " + key);
        }
        ObjectNode config = JSON.createObjectNode().put("schemaVersion", 1).put("channelCode", "PAYERMAX")
            .put("channelAccountId", accountId).put("entityNo", entityNo);
        config.putObject("parameters").put("appId", material.path("appId").asString()).put("merchantNo", material.path("merchantNo").asString())
            .put("keyVersion", material.path("keyVersion").asString()).put("payermaxPublicKey", material.path("payermaxPublicKey").asString());
        config.putObject("credentials").put("merchantPrivateKey", material.path("merchantPrivateKey").asString());
        String token = ChannelAccountVaultMocker.token(marker + name);
        vault.allow(token, config.toString());
        return JSON.createObjectNode().put("channelCode", "PAYERMAX").put("channelAccountId", accountId).put("entityNo", entityNo)
            .put("accountVaultToken", token);
    }

    /** 配置类解析完成后移除测试固定时钟，保留生产 Clock。 */
    public static class RemoveTestClock implements BeanDefinitionRegistryPostProcessor {
        @Override public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
            for (String name : registry.getBeanDefinitionNames()) {
                if (TestClock.class.getName().equals(registry.getBeanDefinition(name).getBeanClassName())) registry.removeBeanDefinition(name);
            }
        }
        @Override public void postProcessBeanFactory(ConfigurableListableBeanFactory factory) { }
    }
}
