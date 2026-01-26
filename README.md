# LumaTrace

Proyecto multimódulo con los siguientes módulos:

- **lumatrace-core**: Motor de watermarking y detección adaptativa.
- **lumatrace-cloud**: API REST para registrar fotos y metadatos (UUID, dispositivo, coordenadas, etc.).

## Estructura

lumatrace/
├─ lumatrace-core/
├─ lumatrace-cloud/
└─ pom.xml (padre)


## Uso

1. Compilar todo el proyecto:

```bash
mvn clean install
```

2. Ejecutar el módulo Cloud:
```bash
cd lumatrace-cloud
mvn spring-boot:run
```

3. Core está pensado para integrarse en la Cloud o en la app cliente.


