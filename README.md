# MDT MQTT Spring Hex (Java 21)
Scaffold Spring Boot + Gradle (hex) para consumir `mdt/pass`, procesar SCAN y persistir en MariaDB `detecciones_tags`. Logs en `./logs`.
Payload:
```json
{"DATATYPE":"SCAN","OBJECT":{"STAGE":"99","DEVICE":"101-AB","MACHINE":"ABDDEE","CSN":["aaaaaaaabb11112233445511","11223344556677889900"]}}
```
Mapeo:
- STAGE → ubicacion_id
- DEVICE/DEVICE_ID → lector_id (dígitos iniciales; "101-AB"→101)
- Cada CSN → fila; CSN→epc; rssi = int(hex últimos 2); MACHINE→machine
Ejecutar:
```
docker compose up -d
gradle bootRun   # o ./gradlew si añades wrapper
```
Publicar ejemplo:
```
mosquitto_pub -t mdt/pass -m '{"DATATYPE":"SCAN","OBJECT":{"STAGE":"99","DEVICE":"101-AB","MACHINE":"ABDDEE","CSN":["aaaaaaaabb11112233445511","11223344556677889900"]}}'
```


## Migraciones con Flyway
- Las migraciones viven en `src/main/resources/db/migration`.
- Ya incluí `V1__init_schema.sql` basado en tu `schema.sql`.
- Flyway corre automáticamente al inicio (**antes** de JPA) porque `spring.jpa.hibernate.ddl-auto=none`.


## Run
```bash
 java -Dspring.profiles.active=prod -jar mdt-mqtt-spring-hex-0.1.0.jar
```


## Environment variables


# variables requeridas en prod (ejemplo)
```bash
 export SPRING_PROFILES_ACTIVE=prod
 export DB_URL='jdbc:mariadb://db-host:3306/mdt?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true'
 export DB_USER='mdt_user'
 export DB_PASSWORD='super_secret'
 export DB_CONN_TIMEOUT_MS=30000
 export DB_VALIDATION_TIMEOUT_MS=5000

 export MQTT_BROKER_URL='tcp://broker.prod:1883'
 export MQTT_CLIENT_ID='mdt-scan-consumer'
 export MQTT_USERNAME=''
 export MQTT_PASSWORD=''
 export MQTT_TOPIC_PASS='mdt/pass'
 export MQTT_TOPIC_ACK='mdt/ack'

 export LOG_LEVEL_ROOT=INFO
 export LOG_LEVEL_APP=INFO
 export LOG_FILE=/var/log/mdt/app.log
 export FLYWAY_ENABLED=false
```

## Payload example

```json
 {
   "DATATYPE": "SCAN",
   "OBJECT": {
     "STAGE": "10",
     "DEVICE": "101-AB",
     "MACHINE": "RECEP-1",
     "CSN": ["AAAAAAAABB11112233445511", "11223344556677889900AA"]
   }
 }
```