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
import com.example.mdt.infrastructure.adapter.db.DbHealthService;
import com.example.mdt.infrastructure.adapter.backlog.BacklogStore;

import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class MqttListenerService implements MqttCallback {

    private static final Logger log = LoggerFactory.getLogger(MqttListenerService.class);

    private final MqttProps props;
    private final ProcessScanUseCase useCase;
    private final DbHealthService dbHealth;
    private final BacklogStore backlogStore;
    private final ObjectMapper mapper = new ObjectMapper();
    private MqttAsyncClient client;

    // ---- RECONNECT STATE ----
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean reconnecting = false;
    private long backoffMs = 500;           // inicio (configurable)
    private final long backoffMaxMs = 10_000; // máx (configurable)
    // -------------------------
    private final AtomicBoolean everConnected = new AtomicBoolean(false);

    public MqttListenerService(MqttProps props, ProcessScanUseCase useCase, DbHealthService dbHealth, BacklogStore backlogStore) {
        this.props = props;
        this.useCase = useCase;
        this.dbHealth = dbHealth;
        this.backlogStore = backlogStore;
    }

    @PostConstruct
    public void start() {
        try {
            ensureClient(); // crea client y setea callback (this)
            MqttConnectionOptions opts = new MqttConnectionOptions();
            opts.setCleanStart(props.cleanStart());
            try { opts.setAutomaticReconnect(true); } catch (Throwable ignored) {}

            if (props.username() != null && !props.username().isBlank()) opts.setUserName(props.username());
            if (props.password() != null && !props.password().isBlank())
                opts.setPassword(props.password().getBytes(StandardCharsets.UTF_8));

            log.info("Connecting to MQTT broker {} ...", props.brokerUrl());
            client.connect(opts); // si falla, cae al catch y NO tumba el contexto
        } catch (Exception e) {
            log.warn("Initial MQTT connect failed: {}", e.getMessage());
        }
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
            try { client.disconnect(); } finally { client.close(); }
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
            String datatype = root.path("DATATYPE").asText(null);
            if (datatype == null || !"SCAN".equalsIgnoreCase(datatype)) {
                log.warn("Ignoring message: unsupported DATATYPE='{}' on topic={}", datatype, topic);
                publishNegativeAck("bad_datatype");
                return;
            }
            JsonNode obj  = root.path("OBJECT");

            List<String> csnList = new ArrayList<>();
            if (obj.has("CSN") && obj.get("CSN").isArray()) {
                for (JsonNode n : obj.get("CSN")) csnList.add(n.asText());
            }

            if (!dbHealth.isAvailable()) {
                log.warn("DB unavailable, enqueuing backlog and NACK (topic={})", topic);
                try { if (backlogStore.isEnabled()) backlogStore.enqueue(body, "db_unavailable"); } catch (Exception ignore) {}
                publishNegativeAck("db_unavailable");
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
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // Violaciones FK/NOT NULL/etc → NACK explícito
            String detail = (ex.getMostSpecificCause() != null) ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
            log.warn("Data integrity error while processing SCAN: {}", detail);
            publishNegativeAck("fk_violation_or_constraint");
        } catch (IllegalArgumentException iae) {
            // Validaciones previas del use case (unknown_device / unknown_stage)
            log.warn("Validation failed: {}", iae.getMessage());
            publishNegativeAck(iae.getMessage()); // p.ej. "unknown_device"
        } catch (Exception e) {
            log.error("Failed to process message", e);
            publishNegativeAck("processing_error");
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
            ObjectNode ack = mapper.createObjectNode();
            ack.put("ok", false);
            ack.put("reason", reason);
            String payload = mapper.writeValueAsString(ack);
            MqttMessage msg = new MqttMessage(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            msg.setQos(props.qos());
            client.publish(props.topicAck(), msg);
            log.debug("Published NACK to {}: {}", props.topicAck(), payload);
        } catch (Exception e) {
            log.warn("Failed to publish negative ACK: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${mqtt.reconnect.check-interval-ms:5000}")
    public void ensureConnectedTask() {
        try {
            ensureClient();
            if (client.isConnected()) return;

            log.info("MQTT not connected, attempting reconnect...");
            MqttConnectionOptions opts = new MqttConnectionOptions();
            opts.setCleanStart(props.cleanStart());
            opts.setAutomaticReconnect(true);

            client.connect(opts); // si falla, cae al catch
            everConnected.set(true);

            // Re-suscribir si no lo haces en connectComplete
            if (props.topicPass() != null && !props.topicPass().isBlank()) {
                client.subscribe(props.topicPass(), props.qos());
                log.info("Re-subscribed to topic {}", props.topicPass());
            }
        } catch (Throwable e) {
            log.warn("MQTT reconnect attempt failed: {}", e.getMessage());
        }
    }

    private synchronized void ensureClient() throws Exception {
        if (client == null) {
            // Usa tus props actuales
            client = new MqttAsyncClient(props.brokerUrl(), props.clientId(), new MemoryPersistence());

            // Si tienes callbacks personalizados, configúralos aquí.
            // Por ejemplo, onDisconnect / connectComplete / messageArrived ya los tienes en tu clase.
            client.setCallback(this);
        }
    }
}
