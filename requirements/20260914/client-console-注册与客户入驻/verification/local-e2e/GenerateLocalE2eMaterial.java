import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Locale;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/**
 * 本机全链路一次性材料：运营/客户签名私钥、HMAC、Vault 本机 AES 根密钥与引导管理员密码。
 * <p>
 * 私密值只写入 0600 的 {@code secrets.env}；{@code bootstrap.sql} 只含公钥 JWK 与 Argon2id 校验串。
 * 唯一参数是已存在、权限 700 的输出目录；拒绝覆盖已有文件，不连接数据库、不打印私密值。
 */
public final class GenerateLocalE2eMaterial {
    private static final SecureRandom RANDOM = new SecureRandom();

    private GenerateLocalE2eMaterial() { }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("唯一参数必须是已存在、权限为 700 的输出目录");
        }
        Path directory = Path.of(arguments[0]).toRealPath();
        if (!Files.getPosixFilePermissions(directory).equals(PosixFilePermissions.fromString("rwx------"))) {
            throw new IOException("输出目录权限必须严格为 700");
        }
        Key operations = key("operations");
        Key customer = key("customer");
        String bootstrapPassword = password();
        String verifier = new Argon2PasswordEncoder(16, 32, 1, 19456, 2).encode(bootstrapPassword);
        String env = String.join("\n",
            "# 本机全链路私密材料；由 GenerateLocalE2eMaterial 生成，勿提交、勿打印。",
            "OPERATIONS_LOCAL_SIGNING_PRIVATE_KEY_BASE64=" + operations.privateKey(),
            "OPERATIONS_LOCAL_HMAC_KEY_BASE64=" + base64(32),
            "OPERATIONS_LOCAL_HMAC_KEY_VERSION=1",
            "CLIENT_LOCAL_SIGNING_PRIVATE_KEY_BASE64=" + customer.privateKey(),
            "CLIENT_LOCAL_HMAC_KEY_BASE64=" + base64(32),
            "CLIENT_LOCAL_HMAC_KEY_VERSION=1",
            "VAULT_LOCAL_AES_ROOT_KEY=" + base64(32),
            "VAULT_LOCAL_AES_KEY_ID=local-e2e-" + urlValue(6).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "0"),
            "E2E_BOOTSTRAP_PASSWORD=" + bootstrapPassword,
            "E2E_BOOTSTRAP_NEW_PASSWORD=" + password(),
            "E2E_ADMIN_EMAIL=operator@example.test",
            "E2E_ADMIN_INITIAL_PASSWORD=" + password(),
            "E2E_ADMIN_PASSWORD=" + password(),
            "");
        String sql = """
            -- 本机全链路 A5：引导管理员与运营/客户签名公钥。只含公钥与 Argon2id 校验串。
            -- 回滚见 local-e2e-execution.md；只在本机 mor_platform 执行一次。
            START TRANSACTION;
            SET @e2e_time = UTC_TIMESTAMP(6);
            INSERT INTO t_user_account (account_id,realm,account_type,login_name,email_vault_token,email_masked,
              email_verified_time,status,consecutive_password_failures,locked_until,last_password_failure_time,
              credential_version,auth_epoch,version,create_time,update_time)
            VALUES (900000000000009001,'OPERATIONS','BOOTSTRAP_ADMIN','admin',NULL,NULL,NULL,
              'ACTIVE',0,NULL,NULL,1,1,1,@e2e_time,@e2e_time);
            INSERT INTO t_password_credential (password_credential_id,account_id,password_verifier_phc,algorithm_code,
              algorithm_version,memory_kib,iterations,parallelism,credential_version,force_change_flag,status,
              password_changed_time,version,create_time,update_time)
            VALUES (900000000000009002,900000000000009001,%s,'ARGON2ID',19,19456,2,1,1,1,'ACTIVE',
              @e2e_time,1,@e2e_time,@e2e_time);
            INSERT INTO t_jwt_signing_key (signing_key_id,realm,kid,algorithm,kms_key_ref,public_jwk_schema_version,
              public_jwk_canonical_json,status,active_slot,not_before,signing_end_time,verification_end_time,
              version,create_time,update_time)
            VALUES
              (900000000000009003,'OPERATIONS',%s,'RS256',%s,1,%s,'ACTIVE',1,@e2e_time,NULL,NULL,1,@e2e_time,@e2e_time),
              (900000000000009004,'CUSTOMER',%s,'RS256',%s,1,%s,'ACTIVE',1,@e2e_time,NULL,NULL,1,@e2e_time,@e2e_time);
            COMMIT;
            """.formatted(sql(verifier),
                sql(operations.kid()), sql("development:operations:" + operations.kid()), sql(operations.jwk()),
                sql(customer.kid()), sql("development:customer:" + customer.kid()), sql(customer.jwk()));
        write(directory.resolve("secrets.env"), env);
        write(directory.resolve("bootstrap.sql"), sql);
        System.out.println("written: secrets.env, bootstrap.sql");
    }

    private static Key key(String realm) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072, RANDOM);
        var pair = generator.generateKeyPair();
        var publicKey = (RSAPublicKey) pair.getPublic();
        String kid = realm + "-local-" + urlValue(12);
        String jwk = "{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"key_ops\":[\"verify\"],\"kid\":\"" + kid
            + "\",\"n\":\"" + unsigned(publicKey.getModulus()) + "\",\"e\":\"" + unsigned(publicKey.getPublicExponent()) + "\"}";
        return new Key(kid, jwk, Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
    }

    private static String password() {
        while (true) {
            String value = urlValue(24);
            if (!value.toLowerCase(Locale.ROOT).matches(".*(admin|operator|paytech|code-mor|example).*")) {
                return value;
            }
        }
    }

    private static String base64(int length) {
        return Base64.getEncoder().encodeToString(bytes(length));
    }

    private static String urlValue(int length) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes(length));
    }

    private static byte[] bytes(int length) {
        byte[] value = new byte[length];
        RANDOM.nextBytes(value);
        return value;
    }

    private static String unsigned(BigInteger value) {
        byte[] raw = value.toByteArray();
        if (raw.length > 1 && raw[0] == 0) {
            raw = java.util.Arrays.copyOfRange(raw, 1, raw.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static String sql(String value) {
        if (!value.matches("[A-Za-z0-9_+./=@:${},\"\\[\\]-]+")) {
            throw new IllegalArgumentException("SQL 材料包含非预期字符");
        }
        return "'" + value + "'";
    }

    private static void write(Path file, String content) throws IOException {
        Files.writeString(file, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
    }

    private record Key(String kid, String jwk, String privateKey) {
        @Override public String toString() { return "Key[" + kid + "]"; }
    }
}
