package com.example.iotplatform.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;

/**
 * MQTT 入站：订阅 device/+/data，把消息投递到 mqttInboundChannel，
 * 由 SensorIngestService 的 @ServiceActivator 消费。配置走 MqttProps。
 */
@Configuration
@EnableConfigurationProperties(MqttProps.class)
public class MqttConfig {

    private final MqttProps props;

    public MqttConfig(MqttProps props) {
        this.props = props;
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{props.url()});
        if (props.username() != null && !props.username().isBlank()) {
            options.setUserName(props.username());
            options.setPassword(props.password() == null ? new char[0] : props.password().toCharArray());
        }
        options.setCleanSession(true);
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageChannel mqttInboundChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageProducer mqttInbound(MqttPahoClientFactory mqttClientFactory,
                                       MessageChannel mqttInboundChannel) {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(props.clientId(), mqttClientFactory, props.topics().split(","));
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInboundChannel);
        return adapter;
    }
}
