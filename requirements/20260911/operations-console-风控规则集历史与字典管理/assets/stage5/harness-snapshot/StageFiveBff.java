import com.paytech.operations.console.bff.OperationsConsoleBffApplication;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;

/** 本地手工联调启动器；真实 BFF 与 Risk，仅 Platform 使用既有协议 Simulator。 */
public class StageFiveBff {
    /** 启动随机 Loopback BFF 和仅用于授权切换/观察的临时控制口。 */
    public static void main(String[] args) throws Exception {
        var platform = new PlatformOperationsSimulator(new AtomicLong());
        platform.useRiskIdentity();
        var context = new SpringApplicationBuilder(OperationsConsoleBffApplication.class).run(
            "--spring.profiles.active=auto", "--server.address=127.0.0.1", "--server.port=0",
            "--spring.cloud.discovery.enabled=false", "--spring.cloud.service-registry.auto-registration.enabled=false",
            "--spring.cloud.nacos.discovery.enabled=false", "--spring.cloud.nacos.discovery.register-enabled=false",
            "--operations.platform-test-base-url=" + platform.baseUrl(),
            "--operations.risk-test-base-url=http://127.0.0.1:18104");
        var control = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        control.createContext("/deny-delete", exchange -> {
            platform.grantPermissions("900000000000000536", "900000000000000537", "900000000000000538",
                "900000000000000539", "900000000000000540");
            exchange.sendResponseHeaders(204, -1); exchange.close();
        });
        control.createContext("/allow", exchange -> {
            platform.useRiskIdentity(); exchange.sendResponseHeaders(204, -1); exchange.close();
        });
        control.createContext("/evidence", exchange -> {
            byte[] bytes = ("{\"platformCalls\":" + platform.requests().size()
                + ",\"unexpectedPlatformCalls\":" + platform.unexpectedRequests().size() + "}").getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        control.start();
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        Files.writeString(Path.of("/tmp/rco16-stage5/bff-runtime.json"),
            "{\"port\":" + port + ",\"controlPort\":" + control.getAddress().getPort() + "}");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { control.stop(0); context.close(); platform.close(); }));
        Thread.currentThread().join();
    }
}
