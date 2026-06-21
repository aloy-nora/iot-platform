package com.example.iotplatform.netty;

import com.example.iotplatform.model.SensorData;
import com.example.iotplatform.service.SensorIngestService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

/**
 * 业务 Handler：收到的已经是「拆好包的 JSON 体」（魔数/长度已被 LengthFieldBasedFrameDecoder 剥掉）。
 * 解析出 deviceId + 传感数据 → 交给统一落库核心。无状态，@Sharable 可被所有连接复用。
 */
@Slf4j
@ChannelHandler.Sharable
public class DeviceFrameHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private final SensorIngestService ingestService;
    private final ObjectMapper objectMapper;

    public DeviceFrameHandler(SensorIngestService ingestService, ObjectMapper objectMapper) {
        this.ingestService = ingestService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf body) throws Exception {
        String json = body.toString(StandardCharsets.UTF_8);
        JsonNode node = objectMapper.readTree(json);
        String deviceId = node.path("deviceId").asText(null);
        if (deviceId == null || deviceId.isEmpty()) {
            log.warn("TCP 帧缺少 deviceId: {}", json);
            return;
        }
        SensorData data = objectMapper.treeToValue(node, SensorData.class); // deviceId 等未知字段被忽略
        ingestService.ingest(deviceId, data);
        log.info("ingest ok (tcp/netty): device={} {}", deviceId, json);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Netty 处理异常: {}", cause.getMessage());
        ctx.close();
    }
}
