# 📘 LumaTrace - Manual de Operaciones (Runbook)

Este documento detalla los pasos exactos para compilar, arrancar y verificar el sistema completo (Core + Cloud) en un entorno local.

---

## 1. Prerrequisitos de Infraestructura
Antes de empezar, asegura que la Base de Datos está activa.

### Docker (PostgreSQL)
Verificar si el contenedor está corriendo:
```powershell
docker ps
```

Si no está en la lista, arráncalo:
```powershell
docker start lumatrace-postgres
```

(Si es la primera vez o lo borraste: docker run --name lumatrace-postgres -e POSTGRES_PASSWORD=secret -e POSTGRES_DB=lumatrace_db -p 5432:5432 -d postgres)

2. Compilación del Sistema (Build)
   Para asegurar que el Core actualizado se inyecta correctamente en el Cloud, compila desde la raíz:

# En la carpeta raíz /lumatrace
```powershell
mvn clean install -DskipTests
```

Debe terminar con BUILD SUCCESS en los 3 módulos.


3. Módulo Core (Línea de Comandos)
   Prueba aislada del motor matemático de marcas de agua.

Ubicación: cd lumatrace-core

Incrustar Marca (Embed)
```powershell
java -jar target/lumatrace-core-1.0.0.jar -e original.jpg secured.jpg
```
Output esperado: [SUCCESS] SECURED in XXms...

Detectar Marca (Detect)
```PowerShell
java -jar target/lumatrace-core-1.0.0.jar -d secured.jpg
```

Output esperado: VERDICT: PASS

4. Módulo Cloud (Servidor API)
   Arrancar el Backend que conecta con la Base de Datos y usa el Core.

Ubicación: cd lumatrace-cloud

Arrancar Servidor
```PowerShell
mvn spring-boot:run
```

Esperar hasta ver: Started LumatraceCloudApplication in ... seconds

Prueba de Integración (Simulación Móvil)
Abre otra terminal y ejecuta:
```PowerShell
Invoke-RestMethod -Uri "http://localhost:8081/api/v1/photos/register" `
  -Method Post `
-ContentType "application/json" `
-Body '{ "userId": "demo_user", "deviceModel": "Runbook Test", "latitude": 40.41, "longitude": -3.70 }'
```

Validación de Éxito
Debes recibir un JSON que contenga el campo watermarkSeed. Ejemplo: "watermarkSeed": -4787565179932966842

🆘 Troubleshooting Rápido
Error: Connection refused al arrancar Spring Boot.

Solución: Docker está apagado. Ejecuta el Paso 1.

Error: Unable to access jarfile en el Core.

Solución: Estás en la carpeta raíz. Entra en cd lumatrace-core.

Error: FAIL en la detección.

Solución: La imagen original.jpg es demasiado plana (blanca/negra). Usa una foto real.




