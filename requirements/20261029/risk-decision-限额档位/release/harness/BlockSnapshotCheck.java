import java.nio.file.*;
import java.util.*;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationCompiler;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationProjection.*;

/**
 * 离线编译 Gateway 快照（块 145 发布前门禁；复制自 REQ-03 release/harness，未改逻辑）：读取 block_compile_check.sh 从断网 MySQL 导出的 6 张网关表，按
 * GatewayConfigurationRepositoryImpl 的加载方式组装 LoadedConfiguration，用候选 Platform JAR 内的生产 GatewayConfigurationCompiler 编译。
 * 参数：<导出目录> <块路由 ID 下界> <块路由 ID 上界> [响应体上限字节] [响应体输出文件]。输出 COMPILE OK / COMPILE FAILED；失败退出码 3。
 * 2026-10-30 REQ-04 增补（D-9）：编译后按 Platform 公开接口同样的转换（GatewaySnapshotConverter）组装响应体，用 Jackson 3 默认 JsonMapper
 * 序列化并输出字节数（与现网 GET /gateway-config/v1/public-snapshot 实测对齐，见 runbook）；给出上限时超过即 SIZE EXCEEDED，退出码 4。
 * 来源：requirements/20261023/commercial-商业关系快照与真实解析接入/release/evidence/snapshot/SnapshotCheck.java（统计改为按路由 ID 区间）。
 */
public class BlockSnapshotCheck {
    static List<String[]> rows(Path dir, String name) throws Exception {
        var out = new ArrayList<String[]>();
        for (String line : Files.readAllLines(dir.resolve(name + ".tsv"))) {
            if (!line.isEmpty()) out.add(line.split("\t", -1));
        }
        return out;
    }
    static String s(String v) { return "NULL".equals(v) ? null : v; }
    static Long l(String v) { return "NULL".equals(v) ? null : Long.valueOf(v); }
    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        long low = Long.parseLong(args[1]), high = Long.parseLong(args[2]);
        var targets = rows(dir, "targets").stream().map(r -> new TargetRow(l(r[0]), s(r[1]), s(r[2]), s(r[3]), s(r[4]))).toList();
        var routes = rows(dir, "routes").stream().map(r -> new RouteRow(l(r[0]), s(r[1]), s(r[2]), s(r[3]), s(r[4]), s(r[5]),
            l(r[6]), s(r[7]), s(r[8]), l(r[9]), s(r[10]), s(r[11]), s(r[12]), s(r[13]), Long.parseLong(r[14]),
            Integer.parseInt(r[15]), Integer.parseInt(r[16]), s(r[17]))).toList();
        Set<Long> ids = new HashSet<>();
        routes.stream().filter(r -> "HUMAN_PERMISSION".equals(r.authorizationMode()) && r.permissionId() != null)
            .forEach(r -> ids.add(r.permissionId()));
        var permissions = rows(dir, "permissions").stream().filter(r -> ids.contains(l(r[0])))
            .map(r -> new PermissionRow(l(r[0]), s(r[1]))).toList();
        var headers = rows(dir, "headers").stream().map(r -> new HeaderRow(l(r[0]), s(r[1]), s(r[2]))).toList();
        var queries = rows(dir, "queries").stream().map(r -> new QueryParameterRow(l(r[0]), s(r[1]))).toList();
        var guards = rows(dir, "guards").stream().map(r -> new GuardRuleRow(l(r[0]), s(r[1]), s(r[2]), l(r[3]), l(r[4]),
            l(r[5]), l(r[6]))).toList();
        var blockCodes = new HashSet<String>();
        long blockLoaded = 0, blockActive = 0;
        for (var r : routes) {
            if (r.routeId() >= low && r.routeId() <= high) {
                blockLoaded++;
                if ("ACTIVE".equals(r.status())) blockActive++;
                blockCodes.add(r.routeCode());
            }
        }
        try {
            var view = new GatewayConfigurationCompiler().compile(new LoadedConfiguration(targets, routes, permissions, headers,
                queries, guards));
            long effective = view.getRoutes().stream().filter(r -> blockCodes.contains(r.routeCode())).count();
            byte[] body = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsBytes(
                new com.paytech.platform.support.ext.rest.gatewayconfiguration.GatewaySnapshotConverter().convertResponse(view));
            System.out.println("COMPILE OK: routes loaded=" + routes.size() + ", effective=" + view.getRoutes().size()
                + ", block loaded=" + blockLoaded + ", block ACTIVE=" + blockActive + ", block effective=" + effective
                + ", snapshot bytes=" + body.length);
            if (args.length > 4) {
                Files.write(Path.of(args[4]), body);
            }
            if (args.length > 3 && body.length >= Long.parseLong(args[3])) {
                System.out.println("SIZE EXCEEDED: snapshot " + body.length + " bytes >= cap " + args[3]);
                System.exit(4);
            }
        } catch (IllegalArgumentException failure) {
            System.out.println("COMPILE FAILED: " + failure.getMessage());
            System.exit(3);
        }
    }
}
