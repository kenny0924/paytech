package com.paytech.vault.test.manual;

import com.alibaba.cloud.nacos.NacosServiceAutoConfiguration;
import com.alibaba.cloud.nacos.discovery.NacosDiscoveryAutoConfiguration;
import com.alibaba.cloud.nacos.discovery.NacosDiscoveryClientConfiguration;
import com.paytech.vault.test.support.GoVaultProcess;
import com.paytech.vault.test.support.VaultDatabaseFixture;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerInterceptor;
import org.springframework.cloud.loadbalancer.config.BlockingLoadBalancerClientAutoConfiguration;
import org.springframework.cloud.loadbalancer.config.LoadBalancerAutoConfiguration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.*;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/** 显式人工门禁：真实 Nacos Discovery / LoadBalancer，不扩展发布的 VaultClient。 */
class NacosDiscoverySmokeTest {
    @Test
    void test_registrationLoadBalancedLivenessAndDisappearance() throws Exception {
        Map<String, String> nacos = requiredNacos();
        Path cache = privateCache();
        try (var database = new VaultDatabaseFixture(); var server = GoVaultProcess.start(database, nacos)) {
            discovery(nacos, cache).run(context -> {
                assertNull(context.getStartupFailure(), "Nacos 测试上下文启动失败");
                var discovery = context.getBean(DiscoveryClient.class);
                await().pollInterval(Duration.ofMillis(200)).atMost(Duration.ofSeconds(30)).until(() ->
                        discovery.getInstances("vault-service").stream().anyMatch(instance -> instance.getPort() == server.port()
                                && instance.getHost().equals("127.0.0.1")));
                assertEquals(1, discovery.getInstances("vault-service").size(), "人工测试 Group 必须只发现当前 Vault 实例");
                probe(context.getBean(LoadBalancerClient.class));
                server.close();
                await().pollInterval(Duration.ofMillis(200)).atMost(Duration.ofSeconds(30)).until(() ->
                        discovery.getInstances("vault-service").stream().noneMatch(instance -> instance.getPort() == server.port()
                                && instance.getHost().equals("127.0.0.1")));
            });
        } catch (Throwable failure) {
            // SDK 自由文本异常可能含凭据；人工边界只保留固定诊断。
            throw new AssertionError("Nacos 人工烟测失败；检查显式本地配置、发现结果和注销条件（SDK 详情不输出）");
        }
        // Java SDK 关闭不证明后台写入已停止。保留此私有目录，待 Maven/Fork 退出后由操作者检查。
    }

    private Map<String, String> requiredNacos() {
        assertTrue(System.getenv("ALIBABA_CLOUD_ACCESS_KEY_ID") == null || System.getenv("ALIBABA_CLOUD_ACCESS_KEY_ID").isEmpty(), "禁用环境中 Alibaba Access Key 后再运行人工门禁");
        assertTrue(System.getenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET") == null || System.getenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET").isEmpty(), "禁用环境中 Alibaba Secret Key 后再运行人工门禁");
        var values = new HashMap<String, String>();
        for (String suffix : List.of("SERVER_ADDRESS", "NAMESPACE", "GROUP", "INSTANCE_IP", "INSTANCE_PORT", "USERNAME", "PASSWORD")) {
            values.put("VAULT_NACOS_" + suffix, VaultDatabaseFixture.required("VAULT_MANUAL_NACOS_" + suffix));
        }
        assertTrue(values.get("VAULT_NACOS_SERVER_ADDRESS").matches("127\\.0\\.0\\.1:[0-9]{1,5}"), "人工 Nacos 仅允许本机 Loopback");
        assertTrue("127.0.0.1".equals(values.get("VAULT_NACOS_INSTANCE_IP")), "人工 Vault 仅允许本机 Loopback");
        assertTrue(values.get("VAULT_NACOS_INSTANCE_PORT").matches("[0-9]{1,5}"), "人工实例端口无效");
        return values;
    }

    private Path privateCache() throws Exception {
        Path root = Path.of(VaultDatabaseFixture.required("VAULT_MANUAL_NACOS_PRIVATE_DIR"));
        assertTrue(root.isAbsolute() && Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS), "先创建人工 SDK 私有目录");
        assertTrue(Files.getPosixFilePermissions(root).equals(PosixFilePermissions.fromString("rwx------")), "人工 SDK 目录必须为 0700");
        assertTrue(root.toString().equals(System.getProperty("JM.LOG.PATH")), "JM.LOG.PATH 必须在人工 Fork 启动前配置");
        assertTrue("false".equals(System.getProperty("nacos.logging.default.config.enabled")), "人工 Fork 必须关闭 SDK 默认日志配置");
        return Files.createTempDirectory(root, "java-naming-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    }

    private ApplicationContextRunner discovery(Map<String, String> nacos, Path cache) {
        var properties = new ArrayList<String>();
        properties.addAll(List.of("spring.application.name=vault-manual-probe", "spring.cloud.nacos.discovery.register-enabled=false",
                "spring.cloud.nacos.discovery.naming-load-cache-at-start=false", "spring.cloud.nacos.discovery.JM.SNAPSHOT.PATH=" + cache,
                "spring.cloud.loadbalancer.retry.enabled=false", "spring.cloud.loadbalancer.cache.enabled=false"));
        Map<String, String> mapping = Map.of("SERVER_ADDRESS", "server-addr", "NAMESPACE", "namespace", "GROUP", "group", "USERNAME", "username", "PASSWORD", "password");
        mapping.forEach((suffix, property) -> properties.add("spring.cloud.nacos.discovery." + property + "=" + nacos.get("VAULT_NACOS_" + suffix)));
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(NacosServiceAutoConfiguration.class,
                NacosDiscoveryAutoConfiguration.class, NacosDiscoveryClientConfiguration.class,
                LoadBalancerAutoConfiguration.class, BlockingLoadBalancerClientAutoConfiguration.class))
                .withPropertyValues(properties.toArray(String[]::new));
    }

    private void probe(LoadBalancerClient loadBalancer) {
        try (var transport = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            var factory = new JdkClientHttpRequestFactory(transport);
            factory.setReadTimeout(Duration.ofSeconds(3));
            var client = RestClient.builder().requestFactory(factory).requestInterceptor(new LoadBalancerInterceptor(loadBalancer)).build();
            assertEquals(200, client.get().uri("http://vault-service/health/liveness").retrieve().toBodilessEntity().getStatusCode().value());
        }
    }
}
