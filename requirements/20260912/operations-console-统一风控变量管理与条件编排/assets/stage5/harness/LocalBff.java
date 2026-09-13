import com.paytech.operations.console.bff.OperationsConsoleBffApplication;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;
import com.sun.net.httpserver.HttpServer;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import java.net.*;
import java.nio.file.*;
import tools.jackson.databind.json.JsonMapper;
public class LocalBff {
 public static void main(String[] args) throws Exception {
  var json=JsonMapper.builder().build();
  int riskPort=json.readTree(Files.readString(Path.of("/tmp/rco19-stage5/risk-runtime.json"))).path("port").asInt();
  var platform=new PlatformOperationsSimulator();platform.useRiskIdentity();
  var app=new SpringApplicationBuilder(OperationsConsoleBffApplication.class).run("--spring.profiles.active=auto","--server.port=0","--server.address=127.0.0.1","--management.server.port=0","--operations.platform-test-base-url="+platform.baseUrl(),"--operations.risk-test-base-url=http://127.0.0.1:"+riskPort);
  var control=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  control.createContext("/permissions",x->{String mode=x.getRequestURI().getQuery(); if("mode=read".equals(mode))platform.grantPermissions("900000000000000536");else if("mode=manage".equals(mode))platform.grantPermissions("900000000000000536","900000000000031101");else if("mode=none".equals(mode))platform.grantPermissions();else {platform.allow(true);platform.useRiskIdentity();} x.sendResponseHeaders(204,-1);x.close();});
  control.createContext("/evidence",x->{byte[] b=json.writeValueAsBytes(java.util.Map.of("calls",platform.requests().size(),"unexpected",platform.unexpectedRequests(),"authorizations",platform.completedAuthorizationCount()));x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(200,b.length);x.getResponseBody().write(b);x.close();});
  control.start();
  Files.writeString(Path.of("/tmp/rco19-stage5/bff-runtime.json"),json.writeValueAsString(java.util.Map.of("port",((WebServerApplicationContext)app).getWebServer().getPort(),"controlPort",control.getAddress().getPort(),"pid",ProcessHandle.current().pid())));
  Runtime.getRuntime().addShutdownHook(new Thread(()->{control.stop(0);app.close();platform.close();}));
  Thread.currentThread().join();
 }
}
