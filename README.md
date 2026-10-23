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
