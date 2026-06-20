package com.example.iotplatform.modbus;

import com.example.iotplatform.config.MqttProps;
import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster;
import com.ghgande.j2mod.modbus.procimg.Register;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Modbus 网关（主机/master）：定时读从机寄存器 → 按缩放解码 → 拼 JSON → 发 MQTT，
 * 复用 W3 的接入链路落库。这就是"协议网关"——把工业协议转成平台统一的 MQTT。
 */
@Slf4j
@Component
@DependsOn("modbusDeviceSimulator")   // 保证从机先启动，主机再连
@ConditionalOnProperty(prefix = "modbus", name = "enabled", havingValue = "true")
public class ModbusPollGateway {

    private final ModbusProps props;
    private final MqttProps mqttProps;
    private ModbusTCPMaster master;
    private MqttClient mqtt;

    public ModbusPollGateway(ModbusProps props, MqttProps mqttProps) {
        this.props = props;
        this.mqttProps = mqttProps;
    }

    @PostConstruct
    public void init() throws Exception {
        master = new ModbusTCPMaster("127.0.0.1", props.slavePort());
        master.connect();
        mqtt = new MqttClient(mqttProps.url(), "modbus-gateway-pub", new MemoryPersistence());
        mqtt.connect();
        log.info("Modbus 网关已连接：从机 127.0.0.1:{} → MQTT {}", props.slavePort(), mqttProps.url());
    }

    @Scheduled(fixedRateString = "${modbus.poll-interval-ms}")
    public void poll() {
        try {
            Register[] r = master.readMultipleRegisters(props.unitId(), 0, 4);
            double temp = r[0].getValue() / 10.0;
            double humi = r[1].getValue() / 10.0;
            int light = r[2].getValue();
            double ph = r[3].getValue() / 100.0;
            String payload = String.format(Locale.US,
                    "{\"temp\":%.1f,\"humi\":%.1f,\"light\":%d,\"soilPh\":%.2f}", temp, humi, light, ph);
            MqttMessage msg = new MqttMessage(payload.getBytes());
            msg.setQos(1);
            mqtt.publish("device/" + props.deviceId() + "/data", msg);
            log.info("Modbus→MQTT device={} {}", props.deviceId(), payload);
        } catch (Exception e) {
            log.error("Modbus 轮询失败: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() throws Exception {
        if (mqtt != null && mqtt.isConnected()) mqtt.disconnect();
        if (master != null) master.disconnect();
    }
}
