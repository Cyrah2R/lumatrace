# LumaTrace Cloud API

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue)

RESTful microservice exposing **LumaTrace Core** capabilities through a secure and scalable API. Designed to integrate into media ingestion workflows and CMS systems.

## API Endpoints

### Register and Protect Image

Generates the cryptographic seed required to protect an image and records the transaction in the database.

**POST** `/api/v1/photos/register`

**Body:**

```json
{
  "userId": "user_123",
  "deviceModel": "iPhone 15 Pro",
  "latitude": 41.3851,
  "longitude": 2.1734
}
```

**Response (200 OK):**

```json
{
  "photoId": "550e8400-e29b-...",
  "watermarkSeed": -2847512206788753832,
  "canonicalHash": "af3cab08..."
}
```

## Environment Variables

For production deployment (Docker/K8s), configure the following variables:

| Variable                   | Description                  | Example                        |
| -------------------------- | ---------------------------- | ------------------------------ |
| LUMATRACE_MASTER_KEY       | Master secret key (Hex/Long) | 0xCAFEBABE...                  |
| SPRING_DATASOURCE_URL      | JDBC connection URL          | jdbc:postgresql://host:5432/db |
| SPRING_DATASOURCE_USERNAME | Database user                | postgres                       |
| SPRING_DATASOURCE_PASSWORD | Database password            | secret                         |
| SERVER_PORT                | Internal container port      | 8081                           |

## Local Development

To start the service without Docker (requires lumatrace-core installed and a local database):

```bash
mvn spring-boot:run
```
