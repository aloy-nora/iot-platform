package com.example.iotplatform.netty;

import com.example.iotplatform.service.SensorIngestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Netty TCP 接入网关（服务端）：接收设备长连接，按自定义协议帧拆包后交业务 Handler。
 * 协议帧：[魔数2B AA55][长度2B bodyLen][JSON 体 bodyLen 字节]
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "netty", name = "enabled", havingValue = "true")
public class NettyTcpServer {

    private final NettyProps props;
    private final DeviceFrameHandler frameHandler;   // @Sharable，所有连接复用一个实例
    private EventLoopGroup boss;
    private EventLoopGroup worker;
    private Channel serverChannel;

    public NettyTcpServer(NettyProps props, SensorIngestService ingestService, ObjectMapper objectMapper) {
        this.props = props;
        this.frameHandler = new DeviceFrameHandler(ingestService, objectMapper);
    }

    @PostConstruct
    public void start() throws InterruptedException {
        boss = new NioEventLoopGroup(1);
        worker = new NioEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap();
        b.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                // 解决 TCP 粘包/半包：按 [偏移2,长度2] 取帧，剥掉前 4 字节(魔数+长度)只留 body
                                .addLast(new LengthFieldBasedFrameDecoder(1024, 2, 2, 0, 4))
                                .addLast(frameHandler);
                    }
                });
        serverChannel = b.bind(props.port()).sync().channel();
        log.info("Netty TCP 接入网关启动 port={}", props.port());
    }

    @PreDestroy
    public void stop() {
        if (serverChannel != null) serverChannel.close();
        if (boss != null) boss.shutdownGracefully();
        if (worker != null) worker.shutdownGracefully();
        log.info("Netty TCP 接入网关已停止");
    }
}
