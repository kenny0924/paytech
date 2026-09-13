import com.paytech.risk.RiskApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import java.nio.file.*;
public class LocalRisk {
 public static void main(String[] args) throws Exception {
  var app = new SpringApplicationBuilder(RiskApplication.class).run("--spring.profiles.active=auto", "--server.port=61171", "--server.address=127.0.0.1", "--redis.key-prefix=rco19-stage5", "--decision.identifier.worker-id=2");
  Files.writeString(Path.of("/tmp/rco19-stage5/risk-runtime.json"), "{\"port\":"+((WebServerApplicationContext)app).getWebServer().getPort()+",\"pid\":"+ProcessHandle.current().pid()+"}");
  Runtime.getRuntime().addShutdownHook(new Thread(app::close));
  Thread.currentThread().join();
 }
}
