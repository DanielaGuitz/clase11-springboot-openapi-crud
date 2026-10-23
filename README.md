# Clase 11 — CRUD Spring Boot + OpenAPI (contract-first) + MariaDB

Microservicio de ejemplo para la Clase 11 de Desarrollo Web: CRUD de **clientes** de la
Oficina del Agua. El contrato de la API se escribe **primero** en OpenAPI (YAML) y el
plugin `openapi-generator-maven-plugin` genera las interfaces y DTOs de Java.

## Requisitos

- JDK 17 (`java -version` debe mostrar 17)
- Maven 3.9+
- Docker (para MariaDB local)

## Cómo correrlo

```bash
# 1. Levantar MariaDB (crea la tabla y datos de prueba la primera vez)
cp .env.example .env        # opcional: ajustar credenciales locales
docker compose up -d

# 2. Generar código desde el YAML, compilar y correr tests
mvn clean verify

# 3. Levantar la API
mvn spring-boot:run
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Contrato: http://localhost:8080/openapi/customers-api.yaml

> Si tu `JAVA_HOME` apunta a otra versión: `export JAVA_HOME=$(/usr/libexec/java_home -v 17)` (macOS).

## Endpoints

| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/api/v1/customers?active=true` | 200 lista |
| GET | `/api/v1/customers/{id}` | 200 / 404 |
| POST | `/api/v1/customers` | 201 + `Location` / 400 / 409 |
| PUT | `/api/v1/customers/{id}` | 200 / 400 / 404 / 409 |
| DELETE | `/api/v1/customers/{id}` | 204 / 404 |

```bash
curl -i -H "Content-Type: application/json" \
  -d '{"fullName":"Luis Mendez","meterCode":"MTR-00005","address":"Zona 5"}' \
  http://localhost:8080/api/v1/customers
```

## Estructura

```
clase11-springboot-openapi-crud/
├── pom.xml                                  # Spring Boot 3.5 + openapi-generator
├── docker-compose.yml                       # MariaDB 11.4 local
├── db/init/01-schema.sql, 02-data.sql       # tabla customers + datos de prueba
├── src/main/resources/
│   ├── application.yml                      # config (DB por variables de entorno)
│   └── static/openapi/customers-api.yaml    # ⭐ EL CONTRATO (fuente de verdad)
├── src/main/java/gt/edu/umg/desarrolloweb/customers/
│   ├── CustomersApplication.java            # @SpringBootApplication
│   ├── controller/                          # capa REST: implementa CustomersApi (generada)
│   ├── service/                             # reglas de negocio + excepciones
│   ├── mapper/                              # DTO <-> entidad
│   ├── repository/                          # Spring Data JPA
│   └── entity/                              # @Entity Customer
└── target/generated-sources/openapi/        # ⚙️ GENERADO: CustomersApi + *Dto (no editar)
```

## Flujo contract-first

1. Editar `customers-api.yaml` (agregar operación, campo, validación…).
2. `mvn generate-sources` (o `mvn compile`) → se regeneran `CustomersApi` y los DTOs.
3. Si agregaste una operación, `CustomerController` **deja de compilar** hasta que la
   implementes (`skipDefaultInterface=true`). El compilador hace cumplir el contrato.

## Notas

- El código generado vive en `target/` y **no se versiona**. En Eclipse/IntelliJ, si no
  reconoce `CustomersApi`: `Maven > Update Project` (o `Reload`) después de `mvn compile`.
- `ddl-auto: validate`: Hibernate **no** crea ni modifica tablas; el esquema lo manda el SQL.
- Para reiniciar la BD desde cero: `docker compose down -v && docker compose up -d`.

---

## Feature 3 � Pagos con Banco (RabbitMQ + Feign + DLQ)

Este microservicio extiende el CRUD de clientes con el procesamiento as�ncrono de pagos hacia un banco simulado.

### Arquitectura

- **Productor (`PagoProductor`)**: `@Scheduled(fixedDelay = 10000)` busca `Pagos` en estado `Pendiente`, los env�a a RabbitMQ (`pagos.exchange` / `pagos.routing-key`) y pasa a `En cola`.
- **Consumidor (`PagoConsumidor`)**: escucha `pagos.queue`, construye `BankPaymentRequestDto`, genera `Idempotency-Key` `EQ-<team>-<pagoId>`, llama al banco v�a Feign (`BankApiClient`).
- **Cliente Feign (`FeignConfig`)**: a�ade `x-api-key`, `X-Correlation-Id` (UUID) y opcionalmente `X-Simulation-Scenario` (desde `-Dbank.simulation.scenario` o `bank.simulation-scenario`/`BANK_SIMULATION_SCENARIO`). Limpia headers por defecto para evitar duplicados.
- **Reintentos/DLQ**: `SimpleRabbitListenerContainerFactory` con `SimpleRetryPolicy` (3 intentos). Errores transitorios (`5xx`, `429`, `503`) se reintentan con backoff exponencial. Tras agotarse, `BankMessageRecoverer` env�a a `pagos.dlx`/`pagos.dlq` y marca el pago como `Fallido - Revisar`. `409 DUPLICATE_PAYMENT` se trata como �xito.
- **Persistencia**: JPA con `PhysicalNamingStrategyStandardImpl` para mantener nombres PascalCase (`Pagos`) en Linux/MariaDB.

### Correr con contenedores de prueba (local)

```bash
# Levantar MariaDB y RabbitMQ para pruebas
docker run -d --name gota-test-db -e MARIADB_ROOT_PASSWORD=root_password \
  -e MARIADB_DATABASE=gota_db -e MARIADB_USER=app_user -e MARIADB_PASSWORD=app_password \
  -p 3308:3306 -v "%cd%/db/init:/docker-entrypoint-initdb.d:ro" mariadb:11.4

docker run -d --name gota-test-rabbit -p 5673:5672 -p 15673:15672 rabbitmq:3-management
```

### Variables de entorno

| Variable | Default | Descripci�n |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | Puerto de la app |
| `DB_URL` | `jdbc:mariadb://localhost:3306/gota_db` | JDBC URL |
| `DB_USERNAME` | `app_user` | Usuario BD |
| `DB_PASSWORD` | `app_password` | Password BD |
| `RABBITMQ_HOST` | `localhost` | Host RabbitMQ |
| `RABBITMQ_PORT` | `5672` | Puerto AMQP |
| `BANK_BASE_URL` | `https://gdsi5lyj34.execute-api.us-east-1.amazonaws.com/prod` | Base URL banco |
| `BANK_API_KEY` | `enmimaquinafunciona` | API key |
| `TEAM_CODE` | `EQUIPO_A` | C�digo de equipo |
| `BANK_SIMULATION_SCENARIO` | (vac�o) | Forzar escenario (`SUCCESS`, `LOST_ACK`, `ERROR_500`, etc.) |

### Ejecuci�n

```bash
set JAVA_HOME=C:\Users\danie\.jdks\ms-17.0.20
set DB_URL=jdbc:mariadb://localhost:3308/gota_db
set RABBITMQ_PORT=5673
set SERVER_PORT=8081
set TEAM_CODE=EQUIPO_A
C:\Users\danie\.trae\tools\maven\latest\bin\mvn.cmd -Dbank.simulation.scenario=SUCCESS spring-boot:run
```

### Escenarios de prueba

Ver [docs/escenarios-prueba.md](docs/escenarios-prueba.md) para procedimientos detallados (�xito, duplicado 409, `ERROR_500` ? 3 reintentos ? DLQ).
