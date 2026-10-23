# Escenarios de prueba manuales (Banco simulado)

Estos escenarios se validaron con MariaDB (gota_db en localhost:3308), RabbitMQ (localhost:5673) y la app corriendo en http://localhost:8081.

## Datos generales

- Base de datos: jdbc:mariadb://localhost:3308/gota_db (usuario pp_user / pp_password)
- Exchange/Routing/Queue/DLQ: pagos.exchange / pagos.routing / pagos.queue / pagos.dlx / pagos.dlq
- API key: enmimaquinafunciona  Equipo: EQUIPO_A
- Endpoint banco: https://gdsi5lyj34.execute-api.us-east-1.amazonaws.com/prod/bank/v1/payments
- Idempotency-Key: EQ-<team>-<pagoId> (ej. EQ-EQUIPO_A-200001)

> La app usa @Scheduled(fixedDelay = 10000) para enviar pagos en estado Pendiente a la cola. Al consumir, se envían headers x-api-key, X-Correlation-Id y, según el escenario, X-Simulation-Scenario.

## 1) Éxito (SUCCESS)

### Preparación

`sql
USE gota_db;
INSERT INTO Pagos (id, lectura_id, monto, metodo, usuario_registro, estado, fecha_pago)
VALUES (200010, 1, 75.00, 'Tarjeta', 1, 'Pendiente', '2026-10-22');
SELECT id, monto, metodo, estado, comprobante FROM Pagos WHERE id = 200010;
`

### Ejecución

Levantar la app con el escenario SUCCESS (puede hacerse por propiedad de sistema o variable de entorno).

**Con Maven (propagando el sistema property):**

`ash
set JAVA_HOME=C:\Users\danie\.jdks\ms-17.0.20
set DB_URL=jdbc:mariadb://localhost:3308/gota_db
set RABBITMQ_PORT=5673
set SERVER_PORT=8081
set TEAM_CODE=EQUIPO_A
C:\Users\danie\.trae\tools\maven\latest\bin\mvn.cmd -Dbank.simulation.scenario=SUCCESS spring-boot:run
`

O con variable de entorno:

`ash
set BANK_SIMULATION_SCENARIO=SUCCESS
...
mvn spring-boot:run
`

El productor enviará el pago en los siguientes 10 segundos. El FeignConfig añadirá X-Simulation-Scenario=SUCCESS.

### Verificación esperada

- Logs: [FEIGN] X-Simulation-Scenario=SUCCESS, [CONSUMIDOR] Pago 200010 registrado en el Banco. Estado=201 receiptId=...
- BD: Pagos.estado = 'Completado' y comprobante = BNK-...
- Cola pagos.queue sin mensaje pendiente tras el consumo exitoso.

## 2) Duplicado / Idempotencia (409 DUPLICATE_PAYMENT)

El banco es global por Idempotency-Key. Si ya existe un pago con esa misma clave, responde 409 DUPLICATE_PAYMENT. La implementación considera esto exitoso (marca el pago como Completado).

**Importante:** No basta con re-enviar el mismo mensaje. Hay que resetear el **mismo registro** de pago a Pendiente para que el productor lo vuelva a enviar con la **misma** Idempotency-Key (EQ-<team>-<pagoId>).

### Preparación (usando el pago existente)

`sql
USE gota_db;
-- Asegurar que el pago exista y esté Completado por una corrida previa
SELECT id, estado, comprobante FROM Pagos WHERE id = 200010;

-- Resetear para forzar nuevo intento con la misma clave de idempotencia
UPDATE Pagos SET estado = 'Pendiente', comprobante = NULL WHERE id = 200010;
SELECT id, estado, comprobante FROM Pagos WHERE id = 200010;
`

Con la app aún corriendo (o al reiniciarla) en el mismo escenario, el productor enviará el pago en ~10s.

### Verificación esperada

- Logs: [CONSUMIDOR] Pago 200010 ya estaba en el Banco (409 DUPLICATE_PAYMENT). Completado. receiptId=...
- BD: estado = 'Completado', comprobante se conserva/actualiza con el receiptId reportado.

## 3) Error transitorio + DLQ (ERROR_500)

El escenario ERROR_500 provoca errores del banco. Con reintentos (max-attempts=3, backoff exponencial) y default-requeue-rejected=false, tras agotar reintentos el mensaje va a pagos.dlq y el pago queda Fallido - Revisar.

### Preparación

`sql
USE gota_db;
INSERT INTO Pagos (id, lectura_id, monto, metodo, usuario_registro, estado, fecha_pago)
VALUES (200011, 1, 60.00, 'Tarjeta', 1, 'Pendiente', '2026-10-22');
SELECT id, estado FROM Pagos WHERE id = 200011;
`

### Ejecución

Levantar la app con ERROR_500:

`ash
set JAVA_HOME=C:\Users\danie\.jdks\ms-17.0.20
set DB_URL=jdbc:mariadb://localhost:3308/gota_db
set RABBITMQ_PORT=5673
set SERVER_PORT=8081
set TEAM_CODE=EQUIPO_A
C:\Users\danie\.trae\tools\maven\latest\bin\mvn.cmd -Dbank.simulation.scenario=ERROR_500 spring-boot:run
`

### Verificación esperada

- Logs: [FEIGN] X-Simulation-Scenario=ERROR_500, tres intentos con Reintentando..., Reintentos agotados para el pago 200011. Enviando a DLQ. (BankMessageRecoverer)
- BD: Pagos.estado = 'Fallido - Revisar'
- RabbitMQ: pagos.queue = 0, pagos.dlq = 1 (mensaje con el id del pago)

## Notas

- Si se pasa 
ull como X-Simulation-Scenario, el banco usa AUTO. Con FeignConfig, al usar escenarios explícitos se elimina cualquier header por defecto.
- x-api-key y X-Correlation-Id siempre se añaden en FeignConfig. El interceptor generado puede añadir x-api-key si no se limpia, por eso se usa 	emplate.removeHeader("x-api-key").
