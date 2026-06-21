package com.example.iotplatform.netty;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Random;

/**
 * TCP 设备模拟器（原生 java.net.Socket 客户端）：
 * 每隔 N 秒，按自定义协议帧 [AA55][len][JSON] 向 Netty 服务端发一条数据。
 * 用原生 Socket（而非 Netty 客户端）也顺带体现"了解 socket 技术"。
 */
@Slf4j
@Component
@DependsOn("nettyTcpServer")   // 保证服务端先起来，客户端再连
@ConditionalOnProperty(prefix = "netty", name = "enabled", havingValue = "true")
public class DeviceTcpSimulator {

    private final NettyProps props;
    private final Random rnd = new Random();
    private Socket socket;

    public DeviceTcpSimulator(NettyProps props) {
        this.props = props;
    }

    @PostConstruct
    public void connect() throws Exception {
        socket = new Socket("127.0.0.1", props.port());
        log.info("TCP 设备模拟器已连接 127.0.0.1:{}", props.port());
    }

    @Scheduled(fixedRateString = "${netty.send-interval-ms}")
    public void send() {
        try {
            if (socket == null || socket.isClosed() || !socket.isConnected()) {
                socket = new Socket("127.0.0.1", props.port());
            }
            String json = String.format(Locale.US,
                    "{\"deviceId\":\"%s\",\"temp\":%.1f,\"humi\":%.1f,\"light\":%d,\"soilPh\":%.2f}",
                    props.deviceId(),
                    18 + rnd.nextDouble() * 17,
                    40 + rnd.nextDouble() * 50,
                    rnd.nextInt(60000),
                    5.5 + rnd.nextDouble() * 2);
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            ByteBuffer frame = ByteBuffer.allocate(4 + body.length);
            frame.put((byte) 0xAA).put((byte) 0x55).putShort((short) body.length).put(body);
            OutputStream out = socket.getOutputStream();
            out.write(frame.array());
            out.flush();
            log.info("TCP 设备发帧 {}", json);
        } catch (Exception e) {
            log.error("TCP 设备发送失败: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void close() throws Exception {
        if (socket != null) socket.close();
    }
}
