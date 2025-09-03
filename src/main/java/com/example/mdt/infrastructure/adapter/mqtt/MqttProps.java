package com.example.mdt.infrastructure.adapter.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mqtt")
public record MqttProps(String brokerUrl, String clientId, String username, String password,
                        String topicPass, String topicAck, int qos, boolean cleanStart) {
}
