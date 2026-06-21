package com.example.iotplatform;

import com.example.iotplatform.alarm.AlarmProps;
import com.example.iotplatform.alarm.PushProps;
import com.example.iotplatform.modbus.ModbusProps;
import com.example.iotplatform.netty.NettyProps;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling                              // W5/W6/W7：模拟器、网关、状态监控用到 @Scheduled
@EnableAsync                                   // W8：告警推送异步
@EnableConfigurationProperties({ModbusProps.class, NettyProps.class, AlarmProps.class, PushProps.class})
public class IotPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(IotPlatformApplication.class, args);
    }

}
