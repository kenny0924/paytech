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
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.*;

/**
 * 外部 API 3e 本机端到端：真实 Platform（测试模块装配提供开发签名 Key 与 Vault Mock HTTP），只连接独立库 mor_platform_external_api。
 * 在 2e 基础上为三个合成支付尝试登记 Vault 渠道账号（CHANNEL_EXECUTION），API Key 每次随机生成，只写入 0600 运行文件供合成渠道验签。
 */
public class PlatformE2e {
    public static void main(String[] args) throws Exception {
        Path runtime = Path.of(args[0]);
        String marker = "external-api-e2e-" + UUID.randomUUID().toString().substring(0, 8);
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
        String database = jdbc.queryForObject("SELECT DATABASE()", String.class);
        if (!"mor_platform_external_api".equals(database)) {
            context.close();
            throw new IllegalStateException("只允许本次独立验证库");
        }
        var operations = context.getBean(OperationsMocker.class);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM t_jwt_signing_key WHERE realm='OPERATIONS' AND status='ACTIVE'", Integer.class) == 0) {
            operations.signingKey("OPERATIONS", OperationsCatalog.OPERATIONS_KID, operations.now().minusSeconds(60));
        }
        var admin = operations.operator(OperatorBuilder.administrator().withoutSession());
        long readerRole = operations.customRole("EXTERNAL_API_READER_" + marker.substring(marker.length() - 8).toUpperCase(), "EXTERNAL_API_READ");
        var reader = operations.operator(OperatorBuilder.administrator().roles(readerRole).withoutSession());

        var random = new SecureRandom();
        byte[] keyBytes = new byte[24];
        random.nextBytes(keyBytes);
        String apiKey = Base64.getUrlEncoder().withoutPadding().encodeToString(keyBytes);
        String prefix = "3" + String.format("%016d", Math.floorMod(random.nextLong(), 10_000_000_000_000_000L));
        var vault = context.getBean(ChannelAccountVaultMocker.class);
        var accounts = new StringJoiner(",", "[", "]");
        for (String outcome : List.of("1", "2", "3")) {
            String attempt = prefix + outcome;
            String token = ChannelAccountVaultMocker.token(marker + attempt);
            vault.allow(token, "{\"schemaVersion\":1,\"channelCode\":\"DEMO\",\"channelAccountId\":\"" + attempt
                + "\",\"entityNo\":\"" + attempt + "\",\"parameters\":{\"outletId\":\"outlet-e2e\"},\"credentials\":{\"apiKey\":\""
                + apiKey + "\"}}");
            accounts.add("{\"attemptId\":\"" + attempt + "\",\"vaultToken\":\"" + token + "\"}");
        }
        Files.writeString(runtime, "{\"port\":" + ((WebServerApplicationContext) context).getWebServer().getPort()
            + ",\"marker\":\"" + marker + "\",\"admin\":\"" + admin.email() + "\",\"reader\":\"" + reader.email()
            + "\",\"password\":\"" + OperatorBuilder.PASSWORD + "\",\"apiKey\":\"" + apiKey + "\",\"accounts\":" + accounts + "}");
        Files.setPosixFilePermissions(runtime, PosixFilePermissions.fromString("rw-------"));
        Runtime.getRuntime().addShutdownHook(new Thread(context::close));
        Thread.currentThread().join();
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
