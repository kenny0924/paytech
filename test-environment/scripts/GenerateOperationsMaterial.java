import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/**
 * 独立 test 环境的人工离线初始化工具：仅生成私密文件，不连接数据库或启动应用。
 * 使用 Java 25 源码启动；唯一参数是已经存在、权限为 700 的输出目录。
 */
public final class GenerateOperationsMaterial {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = PosixFilePermissions.fromString("rw-------");
    private static final String ADMINISTRATOR_EMAIL = "operator@example.test";

    private GenerateOperationsMaterial() { }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("唯一参数必须是已存在、权限为 700 的私密输出目录");
        }
        Path directory = requirePrivateDirectory(Path.of(arguments[0]));
        Path materialPath = directory.resolve("operations-material.json");
        Path sqlPath = directory.resolve("bootstrap.sql");
        requireAbsent(materialPath);
        requireAbsent(sqlPath);
        Material material = generateMaterial();
        writeNewPair(materialPath, materialJson(material), sqlPath, bootstrapSql(material));
        System.out.println(materialPath);
        System.out.println(sqlPath);
    }

    private static Path requirePrivateDirectory(Path supplied) throws IOException {
        if (!Files.isDirectory(supplied, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("输出目录必须已存在且不能是符号链接");
        }
        Path directory = supplied.toRealPath();
        if (!Files.getPosixFilePermissions(directory).equals(DIRECTORY_PERMISSIONS)) {
            throw new IOException("输出目录权限必须严格为 700");
        }
        return directory;
    }

    private static void requireAbsent(Path file) throws IOException {
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("拒绝覆盖已有输出: " + file.getFileName());
        }
    }

    private static Material generateMaterial() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072, RANDOM);
        var pair = generator.generateKeyPair();
        var publicKey = (RSAPublicKey) pair.getPublic();
        String kid = "operations-test-" + randomUrlValue(12);
        String bootstrapPassword = password();
        var encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
        String verifier = encoder.encode(bootstrapPassword);
        String publicJwk = "{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"key_ops\":[\"verify\"],"
            + "\"kid\":" + jsonString(kid) + ",\"n\":" + jsonString(unsigned(publicKey.getModulus()))
            + ",\"e\":" + jsonString(unsigned(publicKey.getPublicExponent())) + "}";
        return new Material(bootstrapPassword, password(), password(), password(),
            Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()),
            Base64.getEncoder().encodeToString(randomBytes(32)), kid, publicJwk, verifier);
    }

    private static String password() {
        while (true) {
            String password = randomUrlValue(32);
            String normalized = password.toLowerCase(Locale.ROOT);
            if (List.of("admin", "operator", "paytech", "code-mor", "example").stream().noneMatch(normalized::contains)) {
                return password;
            }
        }
    }

    private static byte[] randomBytes(int length) {
        byte[] value = new byte[length];
        RANDOM.nextBytes(value);
        return value;
    }

    private static String randomUrlValue(int length) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(length));
    }

    private static String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String materialJson(Material material) {
        return "{\n"
            + "  \"bootstrapPassword\": " + jsonString(material.bootstrapPassword()) + ",\n"
            + "  \"changedBootstrapPassword\": " + jsonString(material.changedBootstrapPassword()) + ",\n"
            + "  \"administratorEmail\": " + jsonString(ADMINISTRATOR_EMAIL) + ",\n"
            + "  \"administratorInitialPassword\": " + jsonString(material.administratorInitialPassword()) + ",\n"
            + "  \"administratorPassword\": " + jsonString(material.administratorPassword()) + ",\n"
            + "  \"privateKeyBase64\": " + jsonString(material.privateKeyBase64()) + ",\n"
            + "  \"hmacKeyBase64\": " + jsonString(material.hmacKeyBase64()) + ",\n"
            + "  \"hmacKeyVersion\": 1,\n"
            + "  \"kid\": " + jsonString(material.kid()) + ",\n"
            + "  \"publicJwk\": " + material.publicJwk() + "\n}\n";
    }

    private static String jsonString(String value) {
        // 所有输入均由本类生成；拒绝逃逸字符，避免把任意外部输入拼入 JSON/SQL。
        if (!value.matches("[A-Za-z0-9_+./=@:-]+")) {
            throw new IllegalArgumentException("生成的材料包含非预期字符");
        }
        return "\"" + value + "\"";
    }

    private static String bootstrapSql(Material material) {
        return """
            -- Review and explicitly apply only to a new isolated test Platform database.
            -- This file is deliberately non-idempotent; never use it to restore a disabled Bootstrap.
            -- Contains a password verifier and PUBLIC JWK only, never passwords or private keys.
            START TRANSACTION;
            SET @operations_material_time = UTC_TIMESTAMP(6);
            INSERT INTO t_user_account (
              account_id,realm,account_type,login_name,email_vault_token,email_masked,email_verified_time,
              status,consecutive_password_failures,locked_until,last_password_failure_time,
              credential_version,auth_epoch,version,create_time,update_time
            ) VALUES (
              900000000000009001,'OPERATIONS','BOOTSTRAP_ADMIN','admin',NULL,NULL,NULL,
              'ACTIVE',0,NULL,NULL,1,1,1,@operations_material_time,@operations_material_time
            );
            INSERT INTO t_password_credential (
              password_credential_id,account_id,password_verifier_phc,algorithm_code,algorithm_version,
              memory_kib,iterations,parallelism,credential_version,force_change_flag,status,
              password_changed_time,version,create_time,update_time
            ) VALUES (
              900000000000009002,900000000000009001,%s,'ARGON2ID',19,
              19456,2,1,1,1,'ACTIVE',@operations_material_time,1,@operations_material_time,@operations_material_time
            );
            INSERT INTO t_jwt_signing_key (
              signing_key_id,realm,kid,algorithm,kms_key_ref,public_jwk_schema_version,
              public_jwk_canonical_json,status,active_slot,not_before,signing_end_time,
              verification_end_time,version,create_time,update_time
            ) VALUES (
              900000000000009003,'OPERATIONS',%s,'RS256',%s,1,
              %s,'ACTIVE',1,@operations_material_time,NULL,NULL,1,@operations_material_time,@operations_material_time
            );
            COMMIT;
            """.formatted(sqlString(material.bootstrapVerifier()), sqlString(material.kid()),
                sqlString("development:operations:" + material.kid()), sqlString(material.publicJwk()));
    }

    private static String sqlString(String value) {
        if (value.indexOf('\'') >= 0 || value.indexOf('\\') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("生成的 SQL 材料包含非预期字符");
        }
        return "'" + value + "'";
    }

    private static void writeNewPair(Path materialPath, String material, Path sqlPath, String sql) throws IOException {
        List<Path> created = new ArrayList<>();
        try {
            writeNew(materialPath, material, created);
            writeNew(sqlPath, sql, created);
        } catch (IOException | RuntimeException exception) {
            // 只清理由本次调用成功创建的文件，绝不清理预先存在或并发调用创建的文件。
            for (Path file : created.reversed()) {
                try { Files.deleteIfExists(file); }
                catch (IOException cleanupFailure) { exception.addSuppressed(cleanupFailure); }
            }
            throw exception;
        }
    }

    private static void writeNew(Path file, String content, List<Path> created) throws IOException {
        try (var channel = FileChannel.open(file,
            Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
            PosixFilePermissions.asFileAttribute(FILE_PERMISSIONS))) {
            created.add(file);
            ByteBuffer bytes = StandardCharsets.UTF_8.encode(content);
            while (bytes.hasRemaining()) { channel.write(bytes); }
            channel.force(true);
        }
        if (!Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS).equals(FILE_PERMISSIONS)) {
            throw new IOException("输出文件权限必须严格为 600: " + file.getFileName());
        }
    }

    private record Material(String bootstrapPassword, String changedBootstrapPassword,
                            String administratorInitialPassword, String administratorPassword,
                            String privateKeyBase64, String hmacKeyBase64, String kid,
                            String publicJwk, String bootstrapVerifier) {
        @Override public String toString() { return "Material[protected]"; }
    }
}
