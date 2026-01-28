LumaTrace – Project Roadmap

Este documento define las fases de evolución del proyecto LumaTrace, desde su estado actual hasta una versión estable y extensible.
No describe el diseño interno (ver docs/design-notes.md), sino qué se construye y en qué orden.

Estado actual (Baseline)

✔️ Proyecto Maven multimódulo funcionando
✔️ Build reproducible (mvn clean install)
✔️ Separación clara de responsabilidades:

lumatrace-core: motor algorítmico puro

lumatrace-cloud: API REST con Spring Boot
✔️ CI básico en GitHub Actions
✔️ Dockerfile funcional
✔️ API mínima operativa (registro de fotos)

Este estado se considera: Foundation Complete

Fase 1 – Estabilización del Core (v0.1)

Objetivo: convertir lumatrace-core en una librería sólida y predecible

Tareas

Tests unitarios deterministas del algoritmo

Validación estadística reproducible (seed fija)

Separar claramente:

generación de firma

inserción

detección

Documentar API pública del Core

Congelar interfaces públicas (@PublicAPI)

Resultado esperado

Core usable como librería standalone

Comportamiento estable entre versiones

Base para benchmarking

Fase 2 – API Cloud coherente (v0.2)

Objetivo: que lumatrace-cloud sea una API clara y extensible

Tareas

DTOs explícitos (no exponer entidades JPA)

Manejo de errores uniforme (Problem Details / RFC 7807)

Validación exhaustiva de inputs

Healthcheck (/actuator/health)

Versionado de API (/api/v1)

Resultado esperado

API lista para integración externa

Contratos claros y estables

Base para clientes móviles o web

Fase 3 – Persistencia y trazabilidad (v0.3)

Objetivo: trazabilidad mínima pero sólida

Tareas

Modelo de dominio definitivo (Photo, Registration, Provenance)

Índices y constraints en BD

Hash canónico reproducible

Auditoría básica (timestamps, source)

Resultado esperado

Registro confiable de eventos

Capacidad de reconstrucción de contexto

Base para integración futura con C2PA/JUMBF

Fase 4 – Robustez algorítmica (v0.4)

Objetivo: aumentar resiliencia sin romper simplicidad

Tareas

Detección multi-escala real

Tolerancia a rotación leve

Métricas automáticas de confianza

Dataset interno de pruebas

Resultado esperado

Detección más estable en escenarios reales

Métricas cuantificables

Menor dependencia de condiciones ideales

Fase 5 – Integración y despliegue (v1.0)

Objetivo: sistema usable de extremo a extremo

Tareas

Docker Compose (API + DB)

Variables de entorno documentadas

Hardening básico (secrets, profiles)

README final coherente con la realidad

Tag v1.0.0

Resultado esperado

Proyecto desplegable en local y cloud

Documentación clara

Base sólida para evolución futura

Fuera de alcance (por ahora)

Explícitamente NO abordado en esta etapa:

DRM

Ofuscación agresiva

Eliminación activa de watermark

Guerra adversarial con IA

Autoridad criptográfica

Nota final

LumaTrace no busca ser “perfecto”, sino útil, honesto y robusto dentro de un modelo de amenazas realista.