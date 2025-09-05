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