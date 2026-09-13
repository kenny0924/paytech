import com.paytech.operations.console.bff.OperationsConsoleBffApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import java.nio.file.*;

/** 真实 BFF 通过本机服务发现连接 Platform；test Profile 允许 localhost HTTP Origin。 */
public class BffAcceptance {
    public static void main(String[] args) throws Exception {
        var context = new SpringApplicationBuilder(OperationsConsoleBffApplication.class).run(
            "--spring.profiles.active=test", "--server.address=127.0.0.1", "--server.port=0",
            "--spring.cloud.nacos.discovery.enabled=false", "--spring.cloud.nacos.discovery.register-enabled=false",
            "--spring.cloud.service-registry.auto-registration.enabled=false",
            "--spring.cloud.discovery.client.simple.instances.platform-support-service[0].uri=" + args[0],
            "--management.server.port=0", "--operations.console-origin=" + args[1],
            "--operations.test-http-enabled=true");
        Files.writeString(Path.of(args[2]), "{\"port\":" + ((WebServerApplicationContext)context).getWebServer().getPort() + "}");
        Runtime.getRuntime().addShutdownHook(new Thread(context::close));
        Thread.currentThread().join();
    }
}
