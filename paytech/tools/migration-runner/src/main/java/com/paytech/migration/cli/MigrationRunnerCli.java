package com.paytech.migration.cli;

import com.paytech.instance.manifest.InstanceManifest;
import com.paytech.instance.manifest.InstanceManifestValidatorImpl;
import com.paytech.instance.manifest.ManifestValidationResult;
import com.paytech.instance.manifest.ManifestViolation;
import com.paytech.instance.manifest.ServiceRuntime;
import com.paytech.migration.model.DatabaseCredentials;
import com.paytech.migration.model.MigrationTarget;
import com.paytech.migration.model.ServiceReport;
import com.paytech.migration.service.ChecksumCalculator;
import com.paytech.migration.service.ChecksumCalculatorImpl;
import com.paytech.migration.service.MigrationExecutor;
import com.paytech.migration.service.MigrationExecutorImpl;
import com.paytech.migration.service.MigrationPlannerImpl;
import com.paytech.migration.service.RunnerConfigurationException;
import com.paytech.migration.service.SeedScriptInspector;
import com.paytech.migration.sql.SqlScriptSplitter;
import com.paytech.migration.sql.SqlScriptSplitterImpl;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@code paytech-migration-runner} 命令行入口：读取实例清单 {@code versions.schema} 作为各服务目标版本，按清单顺序逐个服务、
 * 串行执行子命令。
 *
 * <p>
 * 退出码：0 成功、1 校验失败、2 执行失败、3 参数或环境错误。某个服务执行失败（2）后不再处理后续服务；校验失败的服务
 * 不执行任何语句，后续服务继续处理。
 *
 * @author Claude
 */
public final class MigrationRunnerCli {

    /**
     * 参数或环境错误退出码。
     */
    public static final int EXIT_CONFIGURATION_ERROR = 3;

    private final DatabaseEnvironment databaseEnvironment;

    private final MigrationExecutor migrationExecutor;

    private final ReportWriter reportWriter;

    /**
     * 使用指定环境变量与输出流构造入口。
     *
     * @param environment 环境变量
     * @param out 标准输出
     * @param err 标准错误
     */
    public MigrationRunnerCli(Map<String, String> environment, PrintStream out, PrintStream err) {
        SqlScriptSplitter splitter = new SqlScriptSplitterImpl();
        ChecksumCalculator checksumCalculator = new ChecksumCalculatorImpl();
        this.databaseEnvironment = new DatabaseEnvironment(environment);
        this.migrationExecutor = new MigrationExecutorImpl(new MigrationPlannerImpl(splitter, checksumCalculator),
            new SeedScriptInspector(splitter, checksumCalculator));
        this.reportWriter = new ReportWriter(out, err);
    }

    /**
     * 进程入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        System.exit(new MigrationRunnerCli(System.getenv(), System.out, System.err).run(args));
    }

    /**
     * 执行一次命令。
     *
     * @param args 命令行参数
     * @return 退出码
     */
    public int run(String... args) {
        if (CliArguments.requestsHelp(args)) {
            reportWriter.writeUsage();
            return 0;
        }
        CliArguments arguments = null;
        try {
            arguments = CliArguments.parse(args);
            List<MigrationTarget> targets = targets(arguments, manifest(arguments.manifest()));
            Map<String, DatabaseCredentials> credentials = databaseEnvironment.credentials(
                targets.stream().map(MigrationTarget::service).toList());
            List<ServiceReport> reports = execute(arguments.command(), targets, credentials);
            int exitCode = reports.stream().mapToInt(ServiceReport::exitCode).max().orElse(0);
            reportWriter.write(arguments.command(), arguments.json(), reports, exitCode);
            return exitCode;
        } catch (RunnerConfigurationException exception) {
            reportWriter.writeError(arguments == null ? null : arguments.command(), CliArguments.requestsJson(args),
                exception.getMessage(), EXIT_CONFIGURATION_ERROR);
            return EXIT_CONFIGURATION_ERROR;
        }
    }

    /**
     * 逐个服务执行子命令；执行失败后停止。
     *
     * @param command 子命令
     * @param targets 迁移目标
     * @param credentials 连接信息
     * @return 已处理服务的结果
     */
    List<ServiceReport> execute(MigrationCommand command, List<MigrationTarget> targets,
        Map<String, DatabaseCredentials> credentials) {
        List<ServiceReport> reports = new ArrayList<>();
        for (MigrationTarget target : targets) {
            DatabaseCredentials serviceCredentials = credentials.get(target.service());
            ServiceReport report = switch (command) {
                case PLAN -> migrationExecutor.plan(target, serviceCredentials);
                case VALIDATE -> migrationExecutor.validate(target, serviceCredentials);
                case MIGRATE -> migrationExecutor.migrate(target, serviceCredentials);
                case SEED -> migrationExecutor.seed(target, serviceCredentials);
            };
            reports.add(report);
            if (report.failure() != null) {
                break;
            }
        }
        return reports;
    }

    /**
     * 读取并校验实例清单。
     *
     * @param manifestFile 清单文件
     * @return 清单
     * @throws RunnerConfigurationException 清单不合法
     */
    static InstanceManifest manifest(Path manifestFile) {
        ManifestValidationResult result = new InstanceManifestValidatorImpl().validate(manifestFile);
        if (!result.isValid()) {
            String violations = result.violations().stream().map(ManifestViolation::toString)
                .collect(Collectors.joining("; "));
            throw new RunnerConfigurationException("实例清单不合法: " + violations);
        }
        return result.manifest();
    }

    /**
     * 由清单与参数得出迁移目标；按 {@code versions.schema} 的清单顺序排列。
     *
     * @param arguments 参数
     * @param manifest 清单
     * @return 迁移目标
     * @throws RunnerConfigurationException 服务未登记、无数据库、目标版本越界或 sql-root 不存在
     */
    List<MigrationTarget> targets(CliArguments arguments, InstanceManifest manifest) {
        if (!Files.isDirectory(arguments.sqlRoot())) {
            throw new RunnerConfigurationException("--sql-root 不是目录: " + arguments.sqlRoot());
        }
        Map<String, Integer> versions = manifest.schemaVersions();
        if (arguments.service() != null && !versions.containsKey(arguments.service())) {
            throw new RunnerConfigurationException("实例清单 versions.schema 未登记服务: " + arguments.service());
        }
        List<String> services = arguments.service() != null ? List.of(arguments.service())
            : List.copyOf(versions.keySet());
        if (services.isEmpty()) {
            throw new RunnerConfigurationException("实例清单 versions.schema 未登记任何服务");
        }
        return services.stream().map(service -> target(arguments, manifest, service)).toList();
    }

    /**
     * 一个服务的迁移目标。
     *
     * @param arguments 参数
     * @param manifest 清单
     * @param service 服务名
     * @return 迁移目标
     */
    MigrationTarget target(CliArguments arguments, InstanceManifest manifest, String service) {
        ServiceRuntime runtime = manifest.findService(service);
        if (runtime == null || runtime.database() == null) {
            throw new RunnerConfigurationException("服务 " + service + " 在实例清单中没有数据库，不能登记 Schema 版本");
        }
        int manifestVersion = manifest.schemaVersions().get(service);
        Integer override = arguments.target();
        if (override != null && override > manifestVersion) {
            throw new RunnerConfigurationException("--target " + override + " 高于实例清单 versions.schema." + service
                + "=" + manifestVersion);
        }
        String schemaOverride = databaseEnvironment.schemaOverride(service);
        String expectedDatabase = schemaOverride != null ? schemaOverride : runtime.database();
        return new MigrationTarget(service, expectedDatabase, manifestVersion,
            override != null ? override : manifestVersion, arguments.sqlRoot().resolve(service));
    }
}
