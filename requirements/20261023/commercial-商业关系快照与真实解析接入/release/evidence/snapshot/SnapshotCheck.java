import java.nio.file.*;
import java.util.*;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationCompiler;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationProjection.*;

/** Throwaway: compile the exported Gateway catalog with the production compiler. */
public class SnapshotCheck {
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
        try {
            var view = new GatewayConfigurationCompiler().compile(new LoadedConfiguration(targets, routes, permissions, headers,
                queries, guards));
            long block = view.getRoutes().stream().filter(r -> r.routeCode().startsWith("operations-operating-entity-") || r.routeCode().startsWith("operations-local-partner-") || r.routeCode().startsWith("operations-qualification-")).count();
            System.out.println("COMPILE OK: routes loaded=" + routes.size() + ", effective=" + view.getRoutes().size()
                + ", effective block137=" + block);
        } catch (IllegalArgumentException failure) {
            System.out.println("COMPILE FAILED: " + failure.getMessage());
            System.exit(3);
        }
    }
}
