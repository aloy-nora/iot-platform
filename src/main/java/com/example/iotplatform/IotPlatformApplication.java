package com.example.iotplatform;

import com.example.iotplatform.alarm.AlarmProps;
import com.example.iotplatform.modbus.ModbusProps;
import com.example.iotplatform.netty.NettyProps;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling                              // W5/W6/W7：模拟器、网关、状态监控用到 @Scheduled
@EnableConfigurationProperties({ModbusProps.class, NettyProps.class, AlarmProps.class})
public class IotPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(IotPlatformApplication.class, args);
    }

}
