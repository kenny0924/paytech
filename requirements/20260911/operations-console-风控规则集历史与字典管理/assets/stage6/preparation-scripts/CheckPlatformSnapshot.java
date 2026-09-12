import java.nio.file.Path;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationCompiler;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationProjection.LoadedConfiguration;
import tools.jackson.databind.json.JsonMapper;
/** 用 test 已部署 Platform 编译器离线校验当前及本轮历史与字典目录。 */
public class CheckPlatformSnapshot {
    /** 校验原目录、维护态、禁用注册和启用态目录；不创建 Spring 或数据库连接。 */
    public static void main(String[] args) {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var compiler = new GatewayConfigurationCompiler();
        for (var arg : args) {
            var projection = mapper.readValue(Path.of(arg).toFile(), LoadedConfiguration.class);
            var snapshot = compiler.compile(projection);
            var publicSnapshot = new com.paytech.platform.support.domain.gatewayconfiguration.GatewaySnapshotAssembler().assemble(snapshot);
            mapper.writeValue(Path.of(arg + ".snapshot.json").toFile(), publicSnapshot);
            System.out.println(snapshot.routes().size());
        }
    }
}
