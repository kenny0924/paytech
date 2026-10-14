import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;

/**
 * PayerMax 接入阶段 3：本机合成 PayerMax（127.0.0.1 随机端口），不代表 PayerMax 业务行为，只复现协议形状。
 *
 * <p>与真实协议一致的部分：路径 /aggregate-pay/api/gateway/orderAndPay、/orderQuery；外层 version/keyVersion/requestTime/appId/merchantNo/data；
 * 请求头 sign 为商户私钥对 Body 原文的 SHA256withRSA Base64，响应头 sign 为 PayerMax 私钥对响应原文的签名；验签失败返回 SIGN_VERIFY_FAILED。
 * 启动时随机生成商户与 PayerMax 两对密钥，写入 E2E_RUNTIME/payermax-mock-keys.json（0600）供 Platform 启动器登记 Vault Mock。
 *
 * <p>按 outTradeNo 末位返回：下单 1 受理 PENDING、2 PARAMS_INVALID、3 SYSTEM_ERROR、4 受理但响应用错误私钥签名；
 * 查询 1 SUCCESS（整数金额）、5 PENDING、6 FAILED、7 CLOSED、9 ORDER_NOT_EXIST。日志只记路径、末位、验签与协议检查结果，不记报文。
 */
public class PayerMaxMock {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String APP_ID = "local-payermax-app";
    static final String MERCHANT_NO = "LOCAL0001";
    static KeyPair merchant, payermax, wrong;
    static Path runtime;

    public static void main(String[] args) throws Exception {
        runtime = Path.of(System.getenv("E2E_RUNTIME"));
        merchant = keyPair(); payermax = keyPair(); wrong = keyPair();
        ObjectNode keys = JSON.createObjectNode().put("appId", APP_ID).put("merchantNo", MERCHANT_NO).put("keyVersion", "1")
            .put("merchantPrivateKey", b64(merchant.getPrivate().getEncoded()))
            .put("payermaxPublicKey", b64(payermax.getPublic().getEncoded()));
        write("payermax-mock-keys.json", keys.toString());
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/aggregate-pay/api/gateway/", PayerMaxMock::handle);
        server.start();
        write("payermax-mock.json", "{\"port\":" + server.getAddress().getPort() + "}");
    }

    static void handle(HttpExchange exchange) throws IOException {
        String operation = exchange.getRequestURI().getPath().replace("/aggregate-pay/api/gateway/", "");
        String raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        boolean signed = verify(merchant.getPublic(), raw, exchange.getRequestHeaders().getFirst("sign"));
        JsonNode request = JSON.readTree(raw.isEmpty() ? "{}" : raw);
        String order = request.path("data").path("outTradeNo").asString("");
        String outcome = order.isEmpty() ? "" : order.substring(order.length() - 1);
        boolean envelope = "1.5".equals(request.path("version").asString("")) && "1".equals(request.path("keyVersion").asString(""))
            && APP_ID.equals(request.path("appId").asString("")) && MERCHANT_NO.equals(request.path("merchantNo").asString(""));
        boolean timely = timely(request.path("requestTime").asString(""));
        boolean amountNumber = !"orderAndPay".equals(operation) || request.path("data").path("totalAmount").isNumber();
        ObjectNode reply = JSON.createObjectNode();
        PrivateKey signer = payermax.getPrivate();
        if (!signed) {
            reply.put("code", "SIGN_VERIFY_FAILED").put("msg", "The signature verify failed.");
        } else if (!envelope || !timely || !amountNumber) {
            reply.put("code", "PARAMS_INVALID").put("msg", "envelope, requestTime or totalAmount is invalid");
        } else if ("orderAndPay".equals(operation)) {
            switch (outcome) {
                case "1", "4" -> {
                    reply.put("code", "APPLY_SUCCESS").put("msg", "");
                    reply.putObject("data").put("redirectUrl", "https://cashier.local.invalid/checkout?token=mock-" + order)
                        .put("outTradeNo", order).put("tradeToken", "T" + order).put("status", "PENDING");
                    if ("4".equals(outcome)) signer = wrong.getPrivate();
                }
                case "2" -> reply.put("code", "PARAMS_INVALID").put("msg", "subject is invalid");
                default -> reply.put("code", "SYSTEM_ERROR").put("msg", "System is busy, please try again later.");
            }
        } else if ("orderQuery".equals(operation)) {
            if ("9".equals(outcome)) {
                reply.put("code", "ORDER_NOT_EXIST").put("msg", "The order does not exist.");
            } else {
                String status = switch (outcome) { case "5" -> "PENDING"; case "6" -> "FAILED"; case "7" -> "CLOSED"; default -> "SUCCESS"; };
                reply.put("code", "APPLY_SUCCESS").put("msg", "Success.");
                ObjectNode data = reply.putObject("data").put("outTradeNo", order).put("tradeToken", "T" + order)
                    .put("totalAmount", 10).put("currency", "USD").put("country", "US").put("status", status)
                    .put("resultMsg", "FAILED".equals(status) ? "Transaction declined." : "");
                if ("FAILED".equals(status)) data.put("resultCode", "PAYMENT_FAILED");
                var details = data.putArray("paymentDetails");
                if ("SUCCESS".equals(status)) details.addObject().put("paymentMethodType", "CARD").put("payAmount", 10).put("payCurrency", "USD");
            }
        } else {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        byte[] body = reply.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("sign", sign(signer, body));
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
        journal(JSON.createObjectNode().put("operation", operation).put("outcome", outcome).put("signatureValid", signed)
            .put("envelopeValid", envelope).put("requestTimeValid", timely).put("totalAmountIsNumber", amountNumber)
            .put("code", reply.path("code").asString()).toString());
    }

    static boolean timely(String text) {
        try {
            return text.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}(Z|[+-]\\d{2}:\\d{2})")
                && Duration.between(OffsetDateTime.parse(text).toInstant(), Instant.now()).abs().toSeconds() <= 120;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    static KeyPair keyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    static String sign(PrivateKey key, byte[] data) {
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(key);
            signer.update(data);
            return b64(signer.sign());
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }

    static boolean verify(PublicKey key, String text, String signature) {
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(key);
            verifier.update(text.getBytes(StandardCharsets.UTF_8));
            return signature != null && verifier.verify(Base64.getDecoder().decode(signature));
        } catch (GeneralSecurityException | IllegalArgumentException failure) {
            return false;
        }
    }

    static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    static void write(String name, String content) throws IOException {
        Path file = runtime.resolve(name);
        Files.writeString(file, content);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
    }

    static synchronized void journal(String line) throws IOException {
        Files.writeString(runtime.resolve("payermax-journal.jsonl"), line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
