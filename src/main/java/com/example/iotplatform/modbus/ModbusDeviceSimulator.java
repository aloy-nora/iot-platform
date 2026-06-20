package com.example.iotplatform.modbus;

import com.ghgande.j2mod.modbus.procimg.SimpleProcessImage;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import com.ghgande.j2mod.modbus.slave.ModbusSlave;
import com.ghgande.j2mod.modbus.slave.ModbusSlaveFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * 模拟一台 Modbus 工控设备（从机/slave）：
 * 4 个保持寄存器存 温/湿/光/PH（整数缩放），值每 2 秒变化一次。
 * 真实场景里这是田间的传感器设备，这里用软件模拟、免硬件。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "modbus", name = "enabled", havingValue = "true")
public class ModbusDeviceSimulator {

    private final ModbusProps props;
    private final Random rnd = new Random();
    private ModbusSlave slave;
    private SimpleProcessImage spi;

    public ModbusDeviceSimulator(ModbusProps props) {
        this.props = props;
    }

    @PostConstruct
    public void start() throws Exception {
        spi = new SimpleProcessImage();
        for (int i = 0; i < 4; i++) spi.addRegister(new SimpleRegister(0));
        refresh();
        slave = ModbusSlaveFactory.createTCPSlave(props.slavePort(), 5);
        slave.addProcessImage(props.unitId(), spi);
        slave.open();
        log.info("Modbus 从机模拟器启动 port={} unitId={}", props.slavePort(), props.unitId());
    }

    /** 每 2 秒刷新寄存器值（模拟传感器读数变化） */
    @Scheduled(fixedRate = 2000)
    public void refresh() {
        if (spi == null) return;
        spi.getRegister(0).setValue((int) ((18 + rnd.nextDouble() * 17) * 10));  // 温度 ×10
        spi.getRegister(1).setValue((int) ((40 + rnd.nextDouble() * 50) * 10));  // 湿度 ×10
        spi.getRegister(2).setValue(rnd.nextInt(60000));                         // 光照
        spi.getRegister(3).setValue((int) ((5.5 + rnd.nextDouble() * 2) * 100)); // PH ×100
    }

    @PreDestroy
    public void stop() {
        if (slave != null) slave.close();
    }
}
