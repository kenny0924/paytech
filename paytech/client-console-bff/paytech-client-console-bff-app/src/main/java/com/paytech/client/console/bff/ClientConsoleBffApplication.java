package com.paytech.client.console.bff;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
/** Client Console 无状态通道入口。 */
@SpringBootApplication
public class ClientConsoleBffApplication {
    /** 启动私网 BFF，公开入口由 Gateway 提供。 */
    public static void main(String[] args) { SpringApplication.run(ClientConsoleBffApplication.class, args); }
}
