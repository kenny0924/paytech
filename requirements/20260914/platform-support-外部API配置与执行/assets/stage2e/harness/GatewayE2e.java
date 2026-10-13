import com.paytech.gateway.ApiGatewayApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import java.nio.file.*;
import java.util.*;

/** 真实 Gateway 从真实 Platform 拉取快照和 JWKS，发现只指向本机监听地址。 */
public class GatewayE2e {
    public static void main(String[] args) throws Exception {
        var options = new ArrayList<String>(List.of("--spring.profiles.active=test", "--spring.config.import=classpath:application-auto.yml", "--server.address=127.0.0.1", "--server.port=0",
            "--management.server.port=0", "--gateway.operations.console-origin=" + args[2], "--gateway.operations.test-http-enabled=true",
            "--gateway.operations.guard.redis.namespace=" + args[3],
            "--gateway.platform-snapshot.direct-uri-enabled=false", "--gateway.platform-snapshot.service-id=platform-support-service",
            "--gateway.platform-jwks.direct-uri-enabled=false", "--gateway.platform-jwks.service-id=platform-support-service",
            "--gateway.platform-credential.direct-uri-enabled=false", "--gateway.platform-credential.service-id=platform-support-service",
            "--spring.cloud.discovery.client.simple.instances.platform-support-service[0].uri=" + args[0],
            "--gateway.platform-snapshot.scheduling-enabled=true", "--gateway.platform-snapshot.refresh-interval=PT1S",
            "--spring.cloud.discovery.client.simple.instances.operations-console-bff[0].uri=" + args[1],
            "--spring.cloud.discovery.client.simple.instances." + args[4] + "[0].uri=" + args[2]));
        var context = new SpringApplicationBuilder(ApiGatewayApplication.class).run(options.toArray(String[]::new));
        Files.writeString(Path.of(args[5]), "{\"port\":" + ((WebServerApplicationContext)context).getWebServer().getPort() + "}");
        Runtime.getRuntime().addShutdownHook(new Thread(context::close));
        Thread.currentThread().join();
    }
}
