# LumaTrace Enterprise Framework 🛡️

![Build Status](https://img.shields.io/github/actions/workflow/status/tusuario/lumatrace/maven.yml?branch=main)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green)
![Docker](https://img.shields.io/badge/Docker-Ready-blue)

**LumaTrace** es una solución de marca de agua digital invisible y resiliente, diseñada para la protección de propiedad intelectual en entornos Enterprise. Implementa algoritmos de espectro ensanchado (Spread-Spectrum) con optimización psicovisual.

## 🏗️ Arquitectura Modular

El proyecto sigue una arquitectura hexagonal multi-módulo gestionada con Maven:

* **`lumatrace-core`**: 🧠 El cerebro matemático. Biblioteca pura en Java (sin frameworks) que contiene los algoritmos de incrustación (Watermarking), detección y generación de claves criptográficas.
* **`lumatrace-cloud`**: ☁️ La API RESTful. Implementación en Spring Boot que expone el Core como microservicio, gestiona la persistencia en PostgreSQL y está contenerizada con Docker.

## 🚀 Requisitos

* Java JDK 21
* Maven 3.9+
* Docker Desktop (para despliegue local)

## 🛠️ Despliegue Rápido (Docker)

Hemos orquestado todo el sistema para funcionar en contenedores. Sigue estos pasos para levantar la infraestructura completa (Base de datos + API) en menos de 1 minuto.

### 1. Construir y Levantar todo:
Desde la raíz del proyecto:
```bash
docker-compose up -d --build
```

### 2. Verificar estado
Asegúrate de que ambos contenedores están en estado "Up":
```bash
docker ps
```

### 3. Verificación del Sistema (Smoke Test)
Registra una imagen para generar su semilla única (PowerShell):
```powershell
   $body = @{
    userId = "ingeniero_test"
    deviceModel = "Servidor_Cloud"
    latitude = 40.41
    longitude = -3.70
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8082/api/v1/photos/register" `
  -Method Post `
  -ContentType "application/json" `
  -Body $body
```
📊 Nota sobre Seguridad
La clave maestra se gestiona mediante la variable de entorno LUMATRACE_MASTER_KEY definida en el docker-compose.yml. No hardcodear claves reales en el código fuente.

© 2026 LumaTrace Project.