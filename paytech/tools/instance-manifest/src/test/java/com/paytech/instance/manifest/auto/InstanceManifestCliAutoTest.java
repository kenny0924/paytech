package com.paytech.instance.manifest.auto;

import com.paytech.instance.manifest.cli.InstanceManifestCli;
import com.paytech.instance.manifest.test.ManifestSamples;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 命令行入口的退出码与输出。
 *
 * @see com.paytech.instance.manifest.cli.InstanceManifestCli#run(String[], PrintStream, PrintStream)
 * @author Claude
 */
class InstanceManifestCliAutoTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @TempDir
    private Path workDirectory;

    @Test
    void test_normalFlow() throws IOException {
        Path output = workDirectory.resolve("fragments/main-compose.yaml");

        Assertions.assertEquals(0, run("validate", ManifestSamples.manifest("main.yaml").toString()));
        Assertions.assertTrue(out().contains("校验通过"), out());
        Assertions.assertEquals(0, run("compose", ManifestSamples.manifest("main.yaml").toString(), output.toString()));
        Assertions.assertEquals(ManifestSamples.snapshot("main-compose-fragment.yaml"), Files.readString(output));
    }

    @Test
    void test_businessRuleViolation() {
        Path output = workDirectory.resolve("rejected.yaml");

        Assertions.assertEquals(1, run("validate", ManifestSamples.manifest("invalid-modules.yaml").toString()));
        Assertions.assertTrue(err().contains("modules.loyalty [UNKNOWN_MODULE]"), err());
        Assertions.assertEquals(1, run("compose", ManifestSamples.manifest("invalid-modules.yaml").toString(),
            output.toString()));
        Assertions.assertFalse(Files.exists(output));
    }

    @Test
    void test_invalidParameter() {
        Assertions.assertEquals(1, run());
        Assertions.assertEquals(1, run("render", "main.yaml"));
        Assertions.assertTrue(err().contains("用法"), err());
    }

    @Test
    void test_edgeCase() {
        Path output = workDirectory.resolve("local-environment/compose.generated.yaml");

        Assertions.assertEquals(1, run("compose", ManifestSamples.manifest("main.yaml").toString(), output.toString()));
        Assertions.assertTrue(err().contains("拒绝写入 local-environment/"), err());
        Assertions.assertFalse(Files.exists(output));
    }

    private int run(String... args) {
        return InstanceManifestCli.run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private String out() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return err.toString(StandardCharsets.UTF_8);
    }
}
