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
import java.util.Arrays;
import java.util.Base64;

/**
 * mor-test 客户入驻一次性材料：CUSTOMER 签名私钥与 HMAC、MinIO root/KMS/Platform 访问密钥。
 * <p>
 * 唯一参数是 {@code test-environment/private}（必须已存在且权限 700）。写出三个 0600 文件，拒绝覆盖：
 * {@code client-onboarding.env}（Platform 运行时读取）、{@code minio.env}（追加到 {@code .env.test} 供 Compose 插值）、
 * {@code client-onboarding-signing-key.sql}（只含 CUSTOMER 公钥 JWK）。不连接数据库、不打印私密值，现有 OPERATIONS 材料不动。
 */
public final class GenerateTestClientMaterial {
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 与 test 现有 ID 段不冲突的固定签名密钥行 ID。 */
    private static final long SIGNING_KEY_ID = 9200000000000201L;

    private GenerateTestClientMaterial() { }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("唯一参数必须是 test-environment/private 目录");
        }
        Path directory = Path.of(arguments[0]).toRealPath();
        if (!Files.getPosixFilePermissions(directory).equals(PosixFilePermissions.fromString("rwx------"))) {
            throw new IOException("输出目录权限必须严格为 700");
        }
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072, RANDOM);
        var pair = generator.generateKeyPair();
        var publicKey = (RSAPublicKey) pair.getPublic();
        String kid = "customer-test-" + urlValue(12);
        String jwk = "{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"key_ops\":[\"verify\"],\"kid\":\"" + kid
            + "\",\"n\":\"" + unsigned(publicKey.getModulus()) + "\",\"e\":\"" + unsigned(publicKey.getPublicExponent()) + "\"}";
        String clientEnv = String.join("\n",
            "# mor-test CUSTOMER 认证材料；由 GenerateTestClientMaterial 生成，勿提交、勿打印、勿轮换。",
            "CLIENT_LOCAL_SIGNING_PRIVATE_KEY_BASE64=" + Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()),
            "CLIENT_LOCAL_HMAC_KEY_BASE64=" + Base64.getEncoder().encodeToString(bytes(32)),
            "CLIENT_LOCAL_HMAC_KEY_VERSION=1",
            "");
        String minioEnv = String.join("\n",
            "MOR_MINIO_ROOT_USER=mor-test-root",
            "MOR_MINIO_ROOT_PASSWORD=" + urlValue(24),
            "MOR_MINIO_KMS_SECRET_KEY=mor-test-kms:" + Base64.getEncoder().encodeToString(bytes(32)),
            "MOR_MINIO_PLATFORM_ACCESS_KEY=platform-files-test",
            "MOR_MINIO_PLATFORM_SECRET_KEY=" + urlValue(24),
            "");
        String sql = """
            -- mor-test 客户入驻：CUSTOMER 签名公钥（只含公钥 JWK）。前置：本 ID 与 CUSTOMER 活动密钥均不存在。
            -- 回滚：DELETE FROM t_jwt_signing_key WHERE signing_key_id = %d;
            START TRANSACTION;
            SET @co_time = UTC_TIMESTAMP(6);
            INSERT INTO t_jwt_signing_key (signing_key_id,realm,kid,algorithm,kms_key_ref,public_jwk_schema_version,
              public_jwk_canonical_json,status,active_slot,not_before,signing_end_time,verification_end_time,
              version,create_time,update_time)
            VALUES (%d,'CUSTOMER',%s,'RS256',%s,1,%s,'ACTIVE',1,@co_time,NULL,NULL,1,@co_time,@co_time);
            SELECT signing_key_id, realm, kid, status FROM t_jwt_signing_key WHERE realm = 'CUSTOMER';
            """.formatted(SIGNING_KEY_ID, SIGNING_KEY_ID, sql(kid), sql("development:customer:" + kid), sql(jwk));
        write(directory.resolve("client-onboarding.env"), clientEnv);
        write(directory.resolve("minio.env"), minioEnv);
        write(directory.resolve("client-onboarding-signing-key.sql"), sql);
        System.out.println("written: client-onboarding.env, minio.env, client-onboarding-signing-key.sql (kid " + kid + ")");
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
            raw = Arrays.copyOfRange(raw, 1, raw.length);
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
}
