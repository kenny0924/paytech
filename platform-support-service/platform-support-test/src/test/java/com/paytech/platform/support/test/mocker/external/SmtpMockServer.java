package com.paytech.platform.support.test.mocker.external;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/** 随机 Loopback 端口的最小 SMTP 服务；真实 JavaMail 客户端投递 MIME，未被用例取走的邮件视为意外投递。 */
@TestComponent
public class SmtpMockServer implements Clear, AutoCloseable {

    private final ServerSocket socket;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final BlockingQueue<Mail> mail = new LinkedBlockingQueue<>();
    private final AtomicInteger rejectNext = new AtomicInteger();
    private final AtomicInteger conversations = new AtomicInteger();

    /** 构造时监听随机端口，由动态配置写入 spring.mail.port。 */
    public SmtpMockServer() throws IOException {
        socket = new ServerSocket(0, 20, InetAddress.getByName("127.0.0.1"));
        workers.submit(this::acceptLoop);
    }

    /** JavaMailSender 实际连接的端口。 */
    public int port() {
        return socket.getLocalPort();
    }

    /** 让下一封邮件在 DATA 结束后被服务器拒绝，用于投递失败场景。 */
    public void rejectNextMessage() {
        rejectNext.incrementAndGet();
    }

    /** 有界等待一封真实投递的邮件。 */
    public Mail nextMail() {
        try {
            Mail result = mail.poll(5, TimeUnit.SECONDS);
            assertNotNull(result, "预期的 SMTP 邮件没有送达");
            return result;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("等待 SMTP 邮件被中断");
        }
    }

    /** 当前尚未被用例取走的邮件数量。 */
    public int pendingCount() {
        return mail.size();
    }

    /** 每例开始时没有遗留邮件和故障配置。 */
    public void assertEmpty() {
        assertTrue(mail.isEmpty() && rejectNext.get() == 0, "SMTP Mock 未清理");
    }

    /** 业务发出但用例没有断言的邮件视为意外投递。 */
    public void assertNoUnexpectedMail() {
        assertEquals(0, mail.size(), "存在未断言的 SMTP 投递");
    }

    /** 每例清理邮件和故障配置；监听本身复用到上下文关闭。 */
    @Override
    public void clear() {
        mail.clear();
        rejectNext.set(0);
    }

    /** 上下文关闭时停止监听和会话线程。 */
    @Override
    public void close() throws IOException {
        socket.close();
        workers.close();
    }

    private void acceptLoop() {
        while (!socket.isClosed()) {
            try {
                Socket client = socket.accept();
                workers.submit(() -> conversation(client));
            } catch (IOException failure) {
                if (!socket.isClosed()) {
                    throw new UncheckedIOException(failure);
                }
            }
        }
    }

    /** 解析实际 MIME 邮件，不截获或 Stub 业务端口方法。 */
    private void conversation(Socket client) {
        conversations.incrementAndGet();
        try (client;
            var input = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
            var output = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.US_ASCII))) {
            client.setSoTimeout(5000);
            reply(output, "220 localhost mock SMTP");
            String line;
            while ((line = input.readLine()) != null) {
                if (line.startsWith("EHLO") || line.startsWith("HELO")) {
                    reply(output, "250 localhost");
                } else if (line.equals("DATA")) {
                    reply(output, "354 End data with dot");
                    StringBuilder raw = new StringBuilder();
                    while ((line = input.readLine()) != null && !line.equals(".")) {
                        raw.append(line.startsWith("..") ? line.substring(1) : line).append("\r\n");
                    }
                    if (rejectNext.getAndUpdate(value -> Math.max(0, value - 1)) > 0) {
                        reply(output, "554 rejected by mock");
                        continue;
                    }
                    var message = new MimeMessage(Session.getInstance(new Properties()),
                        new ByteArrayInputStream(raw.toString().getBytes(StandardCharsets.US_ASCII)));
                    mail.add(new Mail(message.getAllRecipients()[0].toString(), message.getContent().toString()));
                    reply(output, "250 accepted");
                } else if (line.equals("QUIT")) {
                    reply(output, "221 bye");
                    return;
                } else {
                    reply(output, "250 accepted");
                }
            }
        } catch (Exception ignored) {
            // SMTP 失败由真实 JavaMail 客户端观察；不把 MIME 或地址写进测试日志。
        }
    }

    private void reply(BufferedWriter output, String value) throws IOException {
        output.write(value + "\r\n");
        output.flush();
    }

    /** 实际投递的收件人和正文；诊断输出不打印内容。 */
    public record Mail(String recipient, String body) {

        /** 返回邮件链接中的查找标识与秘密。 */
        public String[] link() {
            var matcher = Pattern.compile("challengeNo=([0-9a-f-]{36})&secret=([A-Za-z0-9_-]{43})").matcher(body);
            assertTrue(matcher.find(), "邮件中没有认证链接");
            return new String[] {matcher.group(1), matcher.group(2)};
        }

        @Override
        public String toString() {
            return "Mail[REDACTED]";
        }
    }
}
