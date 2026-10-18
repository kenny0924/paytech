package com.paytech.instance.manifest.cli;

import com.paytech.instance.manifest.InstanceManifestValidator;
import com.paytech.instance.manifest.InstanceManifestValidatorImpl;
import com.paytech.instance.manifest.ManifestValidationResult;
import com.paytech.instance.manifest.ManifestViolation;
import com.paytech.instance.manifest.catalog.ServiceCatalog;
import com.paytech.instance.manifest.compose.ComposeFragmentResult;
import com.paytech.instance.manifest.compose.LocalEnvironmentComposeFragmentGenerator;
import com.paytech.instance.manifest.compose.LocalEnvironmentComposeFragmentGeneratorImpl;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 命令行入口：{@code validate <file>} 校验清单；{@code compose <file> <out>} 校验并把 Compose 片段写入 {@code <out>}。
 *
 * <p>
 * 退出码 0 表示成功，1 表示参数错误、校验失败或写入失败。本步不允许把片段写进 {@code local-environment/}。
 *
 * @author Claude
 */
public final class InstanceManifestCli {

    private static final String USAGE = "用法: validate <manifest.yaml> | compose <manifest.yaml> <out.yaml>";

    private final InstanceManifestValidator validator;

    private final LocalEnvironmentComposeFragmentGenerator generator;

    private InstanceManifestCli() {
        ServiceCatalog catalog = ServiceCatalog.loadDefault();
        this.validator = new InstanceManifestValidatorImpl(catalog);
        this.generator = new LocalEnvironmentComposeFragmentGeneratorImpl(catalog, validator);
    }

    /**
     * 进程入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /**
     * 执行命令并返回退出码。
     *
     * @param args 命令行参数
     * @param out 标准输出
     * @param err 错误输出
     * @return 0 成功，1 失败
     */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        InstanceManifestCli cli = new InstanceManifestCli();
        if (args.length == 2 && "validate".equals(args[0])) {
            return cli.validate(Path.of(args[1]), out, err);
        }
        if (args.length == 3 && "compose".equals(args[0])) {
            return cli.compose(Path.of(args[1]), Path.of(args[2]), out, err);
        }
        err.println(USAGE);
        return 1;
    }

    private int validate(Path manifest, PrintStream out, PrintStream err) {
        ManifestValidationResult result = validator.validate(manifest);
        if (!result.isValid()) {
            printViolations(manifest, result.violations(), err);
            return 1;
        }
        out.println("实例清单校验通过: " + manifest + "（" + result.manifest().services().size() + " 个服务）");
        return 0;
    }

    private int compose(Path manifest, Path output, PrintStream out, PrintStream err) {
        Path target = output.toAbsolutePath().normalize();
        for (Path segment : target) {
            if ("local-environment".equals(segment.toString())) {
                err.println("拒绝写入 local-environment/：本步只生成片段文本，请输出到其他目录: " + target);
                return 1;
            }
        }
        ComposeFragmentResult result = generator.generate(manifest);
        if (!result.isGenerated()) {
            printViolations(manifest, result.violations(), err);
            return 1;
        }
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(target, result.fragment());
        } catch (IOException exception) {
            err.println("Compose 片段写入失败: " + target + " (" + exception.getMessage() + ")");
            return 1;
        }
        out.println("已生成 Compose 片段: " + target);
        return 0;
    }

    private static void printViolations(Path manifest, List<ManifestViolation> violations, PrintStream err) {
        err.println("实例清单校验失败: " + manifest + "（" + violations.size() + " 项）");
        violations.forEach(violation -> err.println("  " + violation));
    }
}
