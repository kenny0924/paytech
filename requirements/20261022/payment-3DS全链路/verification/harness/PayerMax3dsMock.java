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
 * 【3DS 全链路阶段 5 扩展副本】自 requirements/20261014/platform-support-PayerMax接入/assets/harness/PayerMaxMock.java 复制，原文件不改。
 * 沿用同一运行目录（密钥、端口、流水），原有全部场景行为不变，只增加：
 * <ul>
 * <li>卡直付下单：cardInfo.dynamic3DS=do3DS 时也返回 PENDING + redirectUrl（模拟 PayerMax 动态 3DS 挑战），且不按订单号末位制造错误；记录每笔订单的金额与币种。</li>
 * <li>查单控制：POST /__control/query {"outTradeNo","status"}（SUCCESS / PENDING / FAILED / CLOSED / NOT_EXIST / MISMATCH），
 *     设置后 orderQuery 按此返回、金额取下单记录（MISMATCH 返回金额 +1）；返回挑战的订单默认 PENDING，其余未设置的订单仍按末位规则。</li>
 * <li>流水增加 dynamic3DS 取值、notifyUrl 是否存在（不记地址）、查单命中的控制状态；GET /__control/journal 返回流水条数。</li>
 * </ul>
 * 原说明：PayerMax 接入阶段 3：本机合成 PayerMax（127.0.0.1 随机端口），不代表 PayerMax 业务行为，只复现协议形状。
 *
 * <p>与真实协议一致的部分：路径 /aggregate-pay/api/gateway/orderAndPay、/orderQuery；外层 version/keyVersion/requestTime/appId/merchantNo/data；
 * 请求头 sign 为商户私钥对 Body 原文的 SHA256withRSA Base64，响应头 sign 为 PayerMax 私钥对响应原文的签名；验签失败返回 SIGN_VERIFY_FAILED。
 * 启动时随机生成商户与 PayerMax 两对密钥，写入 E2E_RUNTIME/payermax-mock-keys.json（0600）供 Platform 启动器登记 Vault Mock。
 *
 * <p>按 outTradeNo 末位返回：下单 1 受理 PENDING、2 PARAMS_INVALID、3 SYSTEM_ERROR、4 受理但响应用错误私钥签名；
 * 查询 1 SUCCESS（整数金额）、5 PENDING、6 FAILED、7 CLOSED、9 ORDER_NOT_EXIST。日志只记路径、末位、验签与协议检查结果，不记报文。
 *
 * <p>退款（PayerMax 退款需求，外层 version 1.1）按 outRefundNo 末位返回：/refund 1 REFUND_PENDING、2 REFUND_SUCCESS、3 REFUND_FAILED（均为受理）、
 * 4 REFUND_FAILED 错误码、5 ORDER_NOT_EXIST、6 SYSTEM_ERROR、7 受理但响应用错误私钥签名；/refundQuery 1 REFUND_PENDING、
 * 2 REFUND_SUCCESS（整数金额）、3 REFUND_FAILED（带 resultMsg）、9 ORDER_NOT_EXIST。退款请求另检查 refundAmount 为 JSON 数字与必填字段。
 *
 * <p>卡直付（PayerMax 卡直付对接，integrate=Direct_Payment）：检查 paymentDetail.cardInfo 五个字段格式；outTradeNo 末位 2、3、4 同收银台下单，
 * 其余按 subject 返回（与 UAT 规则一致）：FAILED → ISSUER_PAYMENT_REJECTED + FAILED（UAT 实测）、PENDING → PENDING、3DS → PENDING + redirectUrl、
 * 其他 → SUCCESS。
 * 日志只记卡字段是否有效，不记卡值。
 *
 * <p>运行目录已有密钥与端口文件时沿用（重启合成渠道不必重启 Platform 或修改出站地址）。
 */
public class PayerMax3dsMock {
    static final java.util.Map<String, String> QUERY = new java.util.concurrent.ConcurrentHashMap<>();
    static final java.util.Map<String, String[]> ORDERS = new java.util.concurrent.ConcurrentHashMap<>();
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String APP_ID = "local-payermax-app";
    static final String MERCHANT_NO = "LOCAL0001";
    static KeyPair merchant, payermax, wrong;
    static Path runtime;

    public static void main(String[] args) throws Exception {
        runtime = Path.of(System.getenv("E2E_RUNTIME"));
        wrong = keyPair();
        Path keyFile = runtime.resolve("payermax-mock-keys.json");
        if (Files.exists(keyFile)) {
            JsonNode saved = JSON.readTree(Files.readString(keyFile));
            merchant = restore(saved.path("merchantPrivateKey").asString(), saved.path("merchantPublicKey").asString());
            payermax = restore(saved.path("payermaxPrivateKey").asString(), saved.path("payermaxPublicKey").asString());
        }
        if (merchant == null || payermax == null) {
            merchant = keyPair(); payermax = keyPair();
            ObjectNode keys = JSON.createObjectNode().put("appId", APP_ID).put("merchantNo", MERCHANT_NO).put("keyVersion", "1")
                .put("merchantPrivateKey", b64(merchant.getPrivate().getEncoded()))
                .put("payermaxPublicKey", b64(payermax.getPublic().getEncoded()))
                .put("merchantPublicKey", b64(merchant.getPublic().getEncoded()))
                .put("payermaxPrivateKey", b64(payermax.getPrivate().getEncoded()));
            write("payermax-mock-keys.json", keys.toString());
        }
        Path portFile = runtime.resolve("payermax-mock.json");
        int port = Files.exists(portFile) ? JSON.readTree(Files.readString(portFile)).path("port").asInt(0) : 0;
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/aggregate-pay/api/gateway/", PayerMax3dsMock::handle);
        server.createContext("/__control/", PayerMax3dsMock::control);
        server.start();
        write("payermax-mock.json", "{\"port\":" + server.getAddress().getPort() + "}");
    }

    static void handle(HttpExchange exchange) throws IOException {
        String operation = exchange.getRequestURI().getPath().replace("/aggregate-pay/api/gateway/", "");
        String raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        boolean signed = verify(merchant.getPublic(), raw, exchange.getRequestHeaders().getFirst("sign"));
        JsonNode request = JSON.readTree(raw.isEmpty() ? "{}" : raw);
        boolean refundOperation = operation.startsWith("refund");
        JsonNode requestData = request.path("data");
        String order = requestData.path(refundOperation ? "outRefundNo" : "outTradeNo").asString("");
        String outcome = order.isEmpty() ? "" : order.substring(order.length() - 1);
        boolean envelope = (refundOperation ? "1.1" : "1.5").equals(request.path("version").asString(""))
            && "1".equals(request.path("keyVersion").asString(""))
            && APP_ID.equals(request.path("appId").asString("")) && MERCHANT_NO.equals(request.path("merchantNo").asString(""));
        boolean timely = timely(request.path("requestTime").asString(""));
        boolean amountNumber = switch (operation) {
            case "orderAndPay" -> requestData.path("totalAmount").isNumber() && (!direct(requestData) || card(requestData));
            case "refund" -> requestData.path("refundAmount").isNumber() && !order.isEmpty() && !requestData.path("outTradeNo").asString("").isEmpty()
                && !requestData.path("refundCurrency").asString("").isEmpty();
            default -> true;
        };
        ObjectNode reply = JSON.createObjectNode();
        PrivateKey signer = payermax.getPrivate();
        if (!signed) {
            reply.put("code", "SIGN_VERIFY_FAILED").put("msg", "The signature verify failed.");
        } else if (!envelope || !timely || !amountNumber) {
            reply.put("code", "PARAMS_INVALID").put("msg", "envelope, requestTime, amount or required field is invalid");
        } else if ("orderAndPay".equals(operation) && direct(requestData) && (requested3ds(requestData) || !"2".equals(outcome) && !"3".equals(outcome))) {
            String subject = requestData.path("subject").asString("");
            boolean dynamic3ds = "do3DS".equals(requestData.path("paymentDetail").path("cardInfo").path("dynamic3DS").asString(""));
            ORDERS.put(order, new String[] {requestData.path("totalAmount").asString(""), requestData.path("currency").asString("")});
            String status = dynamic3ds ? "PENDING" : switch (subject) { case "FAILED" -> "FAILED"; case "PENDING", "3DS" -> "PENDING"; default -> "SUCCESS"; };
            if ("FAILED".equals(status)) {
                reply.put("code", "ISSUER_PAYMENT_REJECTED").put("msg", "Transaction failed at issuer end due to risk control.");
            } else {
                reply.put("code", "APPLY_SUCCESS").put("msg", "Success.");
            }
            ObjectNode data = reply.putObject("data").put("outTradeNo", order).put("tradeToken", "T" + order).put("status", status);
            if ("3DS".equals(subject) || dynamic3ds) {
                data.put("redirectUrl", "https://3ds.local.invalid/challenge?token=mock-" + order + "&acsSecret=S5ACS" + order);
                QUERY.putIfAbsent(order, "PENDING");  // 挑战中的订单在设置查单结果前一律“处理中”，不落入末位规则
            }
            if ("4".equals(outcome) && !dynamic3ds) signer = wrong.getPrivate();
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
        } else if ("orderQuery".equals(operation) && QUERY.containsKey(order)) {
            String control = QUERY.get(order);
            String[] placed = ORDERS.getOrDefault(order, new String[] {"10", "USD"});
            if ("NOT_EXIST".equals(control)) {
                reply.put("code", "ORDER_NOT_EXIST").put("msg", "The order does not exist.");
            } else {
                String status = "MISMATCH".equals(control) ? "SUCCESS" : control;
                java.math.BigDecimal amount = new java.math.BigDecimal(placed[0].isEmpty() ? "10" : placed[0]);
                if ("MISMATCH".equals(control)) amount = amount.add(java.math.BigDecimal.ONE);
                reply.put("code", "APPLY_SUCCESS").put("msg", "Success.");
                ObjectNode data = reply.putObject("data").put("outTradeNo", order).put("tradeToken", "T" + order)
                    .put("totalAmount", amount).put("currency", placed[1]).put("country", "US").put("status", status)
                    .put("resultMsg", "FAILED".equals(status) ? "Transaction declined." : "");
                if ("FAILED".equals(status)) data.put("resultCode", "PAYMENT_FAILED");
                var details = data.putArray("paymentDetails");
                if ("SUCCESS".equals(status)) details.addObject().put("paymentMethodType", "CARD").put("payAmount", amount).put("payCurrency", placed[1]);
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
        } else if ("refund".equals(operation)) {
            switch (outcome) {
                case "1", "2", "3", "7" -> {
                    String status = switch (outcome) { case "2" -> "REFUND_SUCCESS"; case "3" -> "REFUND_FAILED"; default -> "REFUND_PENDING"; };
                    reply.put("code", "APPLY_SUCCESS").put("msg", "Success.");
                    reply.putObject("data").put("outRefundNo", order).put("tradeOrderNo", "T" + requestData.path("outTradeNo").asString())
                        .put("refundTradeNo", "R" + order).put("status", status);
                    if ("7".equals(outcome)) signer = wrong.getPrivate();
                }
                case "4" -> reply.put("code", "REFUND_FAILED").put("msg", "The payment method does not support partial refund.");
                case "5" -> reply.put("code", "ORDER_NOT_EXIST").put("msg", "The orginal order does not exist.");
                default -> reply.put("code", "SYSTEM_ERROR").put("msg", "System is busy, please try again later.");
            }
        } else if ("refundQuery".equals(operation)) {
            if ("9".equals(outcome)) {
                reply.put("code", "ORDER_NOT_EXIST").put("msg", "The order does not exist.");
            } else {
                String status = switch (outcome) { case "2" -> "REFUND_SUCCESS"; case "3" -> "REFUND_FAILED"; default -> "REFUND_PENDING"; };
                reply.put("code", "APPLY_SUCCESS").put("msg", "Success.");
                ObjectNode result = reply.putObject("data").put("outRefundNo", order).put("refundTradeNo", "R" + order)
                    .put("refundAmount", 5).put("refundCurrency", "USD").put("outTradeNo", "P" + order).put("status", status);
                if ("REFUND_FAILED".equals(status)) result.put("resultMsg", "Refund rejected by issuer.");
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
            .put("envelopeValid", envelope).put("direct", direct(requestData)).put("cardValid", !direct(requestData) || card(requestData)).put("requestTimeValid", timely).put("amountValid", amountNumber)
            .put("code", reply.path("code").asString())
            .put("order", order).put("dynamic3DS", requestData.path("paymentDetail").path("cardInfo").path("dynamic3DS").asString(""))
            .put("notifyUrlPresent", !requestData.path("notifyUrl").asString("").isEmpty())
            .put("queryControl", QUERY.getOrDefault(order, "")).put("status", reply.path("data").path("status").asString("")).toString());
    }

    /** 测试控制入口：只接受本机，设置查单结果。 */
    static void control(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        byte[] body;
        if ("POST".equals(exchange.getRequestMethod()) && path.equals("/__control/query")) {
            JsonNode request = JSON.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            QUERY.put(request.path("outTradeNo").asString(), request.path("status").asString());
            body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        } else {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    /** 请求要求动态 3DS（扩展副本：此类请求不按订单号末位制造错误，保证 3DS 场景可重复）。 */
    static boolean requested3ds(JsonNode data) {
        return "do3DS".equals(data.path("paymentDetail").path("cardInfo").path("dynamic3DS").asString(""));
    }

    static boolean direct(JsonNode data) {
        return "Direct_Payment".equals(data.path("integrate").asString(""));
    }

    /** 卡直付的 CARD 支付方式与五个卡字段格式（两位年份）。 */
    static boolean card(JsonNode data) {
        JsonNode detail = data.path("paymentDetail");
        JsonNode info = detail.path("cardInfo");
        return "CARD".equals(detail.path("paymentMethodType").asString(""))
            && info.path("cardIdentifierNo").asString("").matches("[0-9]{12,19}")
            && info.path("cardExpirationMonth").asString("").matches("0[1-9]|1[0-2]")
            && info.path("cardExpirationYear").asString("").matches("[0-9]{2}") && info.path("cvv").asString("").matches("[0-9]{3,4}")
            && !info.path("cardHolderFullName").asString("").isBlank() && "WEB".equals(data.path("terminalType").asString(""));
    }

    static boolean timely(String text) {
        try {
            return text.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}(Z|[+-]\\d{2}:\\d{2})")
                && Duration.between(OffsetDateTime.parse(text).toInstant(), Instant.now()).abs().toSeconds() <= 120;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    static KeyPair restore(String privateKey, String publicKey) throws GeneralSecurityException {
        if (privateKey.isEmpty() || publicKey.isEmpty()) return null;
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return new KeyPair(factory.generatePublic(new java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(publicKey))),
            factory.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey))));
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
