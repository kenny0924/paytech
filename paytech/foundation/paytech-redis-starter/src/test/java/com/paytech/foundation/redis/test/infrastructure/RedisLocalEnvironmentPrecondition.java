package com.paytech.foundation.redis.test.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Fail-fast precondition for the repository-owned loopback Redis. */
public final class RedisLocalEnvironmentPrecondition {

    public static final String HOST = "127.0.0.1";
    public static final int PORT = 16_379;
    private static final String EXPECTED_VERSION = "8.2.1";
    private static final String SETUP = "From paytech/foundation run: set -a; source "
        + "../../local-environment/.env.local; set +a; export "
        + "PAYTECH_REDIS_PASSWORD=\"${PAYTECH_REDIS_PASSWORD:-$MOR_REDIS_PASSWORD}\"; docker compose --env-file "
        + "../../local-environment/.env.local -f ../../local-environment/compose.yaml --profile base ps redis; then run "
        + "the Maven test command in the same shell.";

    private RedisLocalEnvironmentPrecondition() {}

    /** Shell instruction printed when the loopback Redis precondition fails. */
    public static String setupInstruction() {
        return SETUP;
    }

    public static String requirePasswordAndVerify() {
        String password = System.getenv("PAYTECH_REDIS_PASSWORD");
        if (password == null || password.isBlank()) {
            throw unavailable("PAYTECH_REDIS_PASSWORD is missing");
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), 2_000);
            socket.setSoTimeout(2_000);
            OutputStream output = socket.getOutputStream();
            InputStream input = socket.getInputStream();
            assertSimple(send(input, output, "AUTH", password), "+OK", "Redis authentication failed");
            assertSimple(send(input, output, "PING"), "+PONG", "Redis ping failed");
            String info = send(input, output, "INFO", "server");
            if (!info.contains("redis_version:" + EXPECTED_VERSION + "\r\n")) {
                throw unavailable("Redis version must be " + EXPECTED_VERSION);
            }
            return password;
        } catch (AssertionError failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw unavailable("Redis is unavailable or misconfigured");
        }
    }

    private static String send(InputStream input, OutputStream output, String... arguments) throws IOException {
        output.write(("*" + arguments.length + "\r\n").getBytes(StandardCharsets.US_ASCII));
        for (String argument : arguments) {
            byte[] bytes = argument.getBytes(StandardCharsets.UTF_8);
            output.write(("$" + bytes.length + "\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write(bytes);
            output.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        }
        output.flush();
        int marker = input.read();
        if (marker < 0) {
            throw new IOException("Redis closed the connection");
        }
        String header = readLine(input);
        if (marker == '$') {
            int size = Integer.parseInt(header);
            byte[] content = input.readNBytes(size);
            if (content.length != size) {
                throw new IOException("Incomplete Redis response");
            }
            if (input.read() != '\r' || input.read() != '\n') {
                throw new IOException("Invalid Redis response terminator");
            }
            return new String(content, StandardCharsets.UTF_8);
        }
        return (char)marker + header;
    }

    private static String readLine(InputStream input) throws IOException {
        var line = new StringBuilder();
        int previous = -1;
        while (true) {
            int value = input.read();
            if (value < 0) {
                throw new IOException("Incomplete Redis response line");
            }
            if (previous == '\r' && value == '\n') {
                line.setLength(line.length() - 1);
                return line.toString();
            }
            line.append((char)value);
            previous = value;
        }
    }

    private static void assertSimple(String actual, String expected, String failure) {
        if (!actual.equals(expected)) {
            throw unavailable(failure);
        }
    }

    private static AssertionError unavailable(String reason) {
        return new AssertionError(reason + ". " + SETUP);
    }
}
