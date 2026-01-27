# LumaTrace Cloud API

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue)

Microservicio RESTful que expone las capacidades de **LumaTrace Core** a través de una API segura y escalable. Diseñado para integrarse en flujos de trabajo de ingesta de medios y sistemas CMS.

## 🔌 API Endpoints

### Registrar y Proteger Imagen
Genera la semilla criptográfica necesaria para proteger una imagen y registra la transacción en base de datos.

**POST** `/api/v1/photos/register`

**Body:**
```json
{
  "userId": "user_123",
  "deviceModel": "iPhone 15 Pro",
  "latitude": 41.3851,
  "longitude": 2.1734
}

Response (200 OK):
{
  "photoId": "550e8400-e29b-...",
  "watermarkSeed": -2847512206788753832,
  "canonicalHash": "af3cab08..."
}


⚙️ Variables de Entorno
Para despliegue en producción (Docker/K8s), es necesario configurar estas variables:

Variable	Descripción	Ejemplo
LUMATRACE_MASTER_KEY	Clave maestra secreta (Hex/Long)	0xCAFEBABE...
SPRING_DATASOURCE_URL	URL de conexión JDBC	jdbc:postgresql://host:5432/db
SPRING_DATASOURCE_USERNAME	Usuario de BD	postgres
SPRING_DATASOURCE_PASSWORD	Contraseña de BD	secret
SERVER_PORT	Puerto interno del contenedor	8081

🛠️ Desarrollo Local
Para arrancar el servicio sin Docker (requiere lumatrace-core instalado y una BD local):
mvn spring-boot:run