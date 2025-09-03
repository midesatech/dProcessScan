package com.example.mdt.infrastructure.adapter.mqtt;

import com.example.mdt.domain.model.Scan;
import com.example.mdt.domain.usecase.ProcessScanUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


@Component
public class MqttListenerService implements MqttCallback {

    private static final Logger log = LoggerFactory.getLogger(MqttListenerService.class);

    private final MqttProps props;
    private final ProcessScanUseCase useCase;
    private final ObjectMapper mapper = new ObjectMapper();
    private MqttAsyncClient client;

    // ---- RECONNECT STATE ----
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean reconnecting = false;
    private long backoffMs = 500;           // inicio (configurable)
    private final long backoffMaxMs = 10_000; // máx (configurable)
    // -------------------------

    public MqttListenerService(MqttProps props, ProcessScanUseCase useCase) {
        this.props = props;
        this.useCase = useCase;
    }

    @PostConstruct
    public void start() throws Exception {
        connectAndSubscribe();
    }

    private void connectAndSubscribe() throws Exception {
        String clientId = (props.clientId() == null || props.clientId().isBlank())
                ? "mdt-" + java.util.UUID.randomUUID()
                : props.clientId();

        client = new MqttAsyncClient(props.brokerUrl(), clientId, new MemoryPersistence());
        client.setCallback(this);

        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(props.cleanStart());
        if (props.username() != null && !props.username().isBlank()) options.setUserName(props.username());
        if (props.password() != null && !props.password().isBlank())
            options.setPassword(props.password().getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // Si tu versión de Paho v5 lo soporta, lo activamos además del backoff manual:
        try {
            options.setAutomaticReconnect(true);
        } catch (Throwable ignored) {
            // algunas versiones no exponen esta opción en v5; nuestro backoff manual seguirá funcionando
        }

        log.info("Connecting to MQTT broker {} ...", props.brokerUrl());
        client.connect(options).waitForCompletion();
        subscribeAll();
        log.info("Connected and subscribed to {}", props.topicPass());
        // reset backoff al conectar
        backoffMs = 500;
        reconnecting = false;
    }

    private void subscribeAll() throws Exception {
        client.subscribe(props.topicPass(), props.qos());
    }

    @PreDestroy
    public void stop() throws Exception {
        scheduler.shutdownNow();
        if (client != null) {
            try {
                client.disconnect();
            } finally {
                client.close();
            }
        }
    }

    // ---------- Callbacks ----------

    @Override
    public void disconnected(MqttDisconnectResponse reason) {
        log.warn("MQTT disconnected: code={} msg={}", reason.getReturnCode(), reason.getReasonString());
        scheduleReconnect();
    }

    @Override
    public void mqttErrorOccurred(org.eclipse.paho.mqttv5.common.MqttException e) {
        log.error("MQTT error", e);
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("MQTT connectComplete: reconnect={} serverURI={}", reconnect, serverURI);
        // En algunos casos (cleanStart=true) hay que re-suscribirse explícitamente
        try {
            if (client != null && client.isConnected()) {
                subscribeAll();
                log.info("Re-subscribed after connectComplete");
            }
        } catch (Exception e) {
            log.error("Failed to resubscribe on connectComplete", e);
            scheduleReconnect();
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        final String body = new String(message.getPayload(), StandardCharsets.UTF_8);
        log.info("Message arrived on {}: {}", topic, body);
        try {
            JsonNode root = mapper.readTree(body);

            // Validate DATATYPE
            String datatype = root.path("DATATYPE").asText(null);
            if (datatype == null || !"SCAN".equalsIgnoreCase(datatype)) {
                log.warn("Ignoring message: unsupported DATATYPE='{}' on topic={}", datatype, topic);
                publishNegativeAck("bad_datatype");
                return;
            }

            JsonNode obj = root.path("OBJECT");
            if (obj.isMissingNode() || obj.isNull()) {
                log.warn("Ignoring SCAN: missing OBJECT node (topic={})", topic);
                publishNegativeAck("missing_object");
                return;
            }

            List<String> csnList = new ArrayList<>();
            if (obj.has("CSN") && obj.get("CSN").isArray()) {
                for (JsonNode n : obj.get("CSN")) csnList.add(n.asText());
            } else {
                log.warn("Ignoring SCAN: OBJECT.CSN is missing or not an array (topic={})", topic);
                publishNegativeAck("invalid_csn");
                return;
            }
            Scan scan = new Scan(
                    root.path("DATATYPE").asText(null),
                    obj.path("STAGE").asText(null),
                    obj.path("DEVICE").asText(null),
                    obj.path("MACHINE").asText(null),
                    csnList
            );

            int inserted = useCase.process(scan);
            log.info("Processed SCAN: {} detections inserted", inserted);

            if (props.topicAck() != null && !props.topicAck().isBlank()) {
                ObjectNode ackNode = mapper.createObjectNode();
                ackNode.put("ok", true);
                ackNode.put("inserted", inserted);
                String ack = mapper.writeValueAsString(ackNode);

                var msg = new MqttMessage(ack.getBytes(StandardCharsets.UTF_8));
                msg.setQos(props.qos());
                client.publish(props.topicAck(), msg);
                log.debug("Published ACK to {}: {}", props.topicAck(), ack);
            }
        } catch (Exception e) {
            log.error("Failed to process message", e);
        }
    }

    @Override
    public void deliveryComplete(org.eclipse.paho.mqttv5.client.IMqttToken token) { /* no-op */ }

    @Override
    public void authPacketArrived(int reasonCode, org.eclipse.paho.mqttv5.common.packet.MqttProperties properties) { /* no-op */ }

    // ---------- Reconnect logic ----------

    private void scheduleReconnect() {
        if (reconnecting) return;
        reconnecting = true;
        long delay = backoffMs;
        log.warn("Scheduling reconnect in {} ms", delay);
        scheduler.schedule(this::attemptReconnect, delay, TimeUnit.MILLISECONDS);
        // próximo backoff
        backoffMs = Math.min((long) (backoffMs * 1.8), backoffMaxMs);
    }

    private void attemptReconnect() {
        try {
            if (client == null) {
                connectAndSubscribe();
                return;
            }
            if (!client.isConnected()) {
                log.info("Attempting MQTT reconnect...");
                client.reconnect(); // si falla, lanzará excepción y reprogramamos
            }
            // Si reconectó, reseteamos estado/backoff en connectComplete()
        } catch (Exception e) {
            log.warn("Reconnect attempt failed: {}", e.getMessage());
            reconnecting = false; // para poder volver a programar
            scheduleReconnect();
        }

    }

    private void publishNegativeAck(String reason) {
        try {
            if (props.topicAck() == null || props.topicAck().isBlank()) return;
            var obj = mapper.createObjectNode();
            obj.put("ok", false);
            obj.put("reason", reason);
            String payload = mapper.writeValueAsString(obj);
            var msg = new org.eclipse.paho.mqttv5.common.MqttMessage(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            msg.setQos(props.qos());
            client.publish(props.topicAck(), msg);
            log.debug("Published NACK to {}: {}", props.topicAck(), payload);
        } catch (Exception e) {
            log.warn("Failed to publish negative ACK: {}", e.getMessage());
        }
    }
}

