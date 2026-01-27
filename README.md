# LumaTrace Enterprise Framework

![Build Status](https://img.shields.io/github/actions/workflow/status/tusuario/lumatrace/maven.yml?branch=main)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green)
![Docker](https://img.shields.io/badge/Docker-Ready-blue)

**LumaTrace** is an invisible and resilient digital watermarking solution designed for intellectual property protection 
in Enterprise environments. It implements Spread-Spectrum algorithms with psycho-visual optimization.

## Modular Architecture

The project follows a multi-module hexagonal architecture managed with Maven:

* **`lumatrace-core`**: The mathematical brain. A pure Java library (no frameworks) containing watermark embedding, 
* detection, and cryptographic key generation algorithms.
* **`lumatrace-cloud`**: The RESTful API. A Spring Boot implementation that exposes the Core as a microservice, manages 
* PostgreSQL persistence, and is containerized with Docker.

## Requirements

* Java JDK 21
* Maven 3.9+
* Docker Desktop (for local deployment)

## Quick Deployment (Docker)

The entire system has been orchestrated to run in containers. Follow these steps to bring up the full infrastructure 
(Database + API) in under one minute.

### 1. Build and Start Everything

From the project root:

```bash
docker-compose up -d --build
```

### 2. Verify Status

Ensure both containers are in the "Up" state:

```bash
docker ps
```

### 3. System Verification (Smoke Test)

Register an image to generate its unique seed (PowerShell):

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

## Security Note
The master key is managed via the LUMATRACE_MASTER_KEY environment variable defined in docker-compose.yml. Never hardcode 
real keys in the source code.

© 2026 LumaTrace Project.
