# LumaTrace – Internal Design Notes
(Private – Not for publication)

---

# LumaTrace – Notas internas de diseño
(Privado – No público)

---

## 1. Problem Statement (EN)

C2PA manifests provide cryptographic provenance guarantees but are fragile under:
- Metadata stripping
- Format conversion
- Cropping
- Analog recapture (screen-to-camera)

This creates a gap ("Analog Hole") where provenance is lost even if intent remains authentic.

LumaTrace is designed to act as a **Soft Binding layer**, not as a cryptographic authority.

---

## 1. Planteamiento del problema (ES)

Los manifiestos C2PA proporcionan garantías criptográficas de procedencia, pero son frágiles frente a:
- Eliminación de metadatos
- Conversión de formato
- Recortes
- Recaptura analógica (pantalla a cámara)

Esto genera una brecha (“Analog Hole”) en la que la procedencia se pierde aunque el contenido siga siendo legítimo.

LumaTrace se diseña como una **capa de Soft Binding**, no como una autoridad criptográfica.

---

## 2. Design Goals (EN)

Primary goals:
- Invisible watermarking
- Blind detection (no original required)
- Robustness against common non-destructive attacks
- Deterministic behavior tied to C2PA metadata
- Statistical detectability with low false positives

Non-goals:
- Perfect cryptographic security
- DRM enforcement
- Adversarial watermark arms race

---

## 2. Objetivos de diseño (ES)

Objetivos principales:
- Marca de agua invisible
- Detección ciega (sin imagen original)
- Robustez frente a ataques no destructivos comunes
- Comportamiento determinista ligado a metadatos C2PA
- Detección estadística con baja tasa de falsos positivos

No objetivos:
- Seguridad criptográfica perfecta
- Aplicación de DRM
- Carrera armamentística contra atacantes activos

---

## 3. High-Level Architecture (EN)

Pipeline:
1. Metadata → deterministic seed (SHA-256)
2. Seed → Gaussian PRNG signature (64x64)
3. Signature → spatial tiling (periodic)
4. Image → local activity estimation
5. Signal → adaptive gain modulation
6. Injection → blue-dominant RGB perturbation

Detection:
1. Candidate image → multi-scale resize
2. Fold image into 64x64 accumulator
3. Correlate against reference signature
4. Compute Z-score distribution
5. Derive statistical verdict

---

## 3. Arquitectura de alto nivel (ES)

Proceso de inserción:
1. Metadatos → semilla determinista (SHA-256)
2. Semilla → firma pseudoaleatoria gaussiana (64x64)
3. Firma → teselado espacial periódico
4. Imagen → estimación de actividad local
5. Señal → modulación adaptativa de ganancia
6. Inserción → perturbación RGB con predominio del canal azul

Proceso de detección:
1. Imagen candidata → reescalado multi-escala
2. Plegado de la imagen en un acumulador 64x64
3. Correlación con la firma de referencia
4. Cálculo de la distribución de Z-scores
5. Obtención del veredicto estadístico

---

## 4. Key Design Decisions (EN)

### 4.1 Blue Channel Bias
Chosen due to human visual system insensitivity and JPEG chroma subsampling behavior.

### 4.2 Macro-block Signature
4x4 macro-blocks improve robustness against blur and analog capture.

### 4.3 Statistical Detection
Z-score based detection avoids fixed thresholds and adapts to the noise floor.

---

## 4. Decisiones clave de diseño (ES)

### 4.1 Predominio del canal azul
Elegido por la baja sensibilidad del sistema visual humano y el comportamiento del submuestreo cromático en JPEG.

### 4.2 Firma por macro-bloques
El uso de macro-bloques 4x4 mejora la robustez frente a desenfoque y recaptura analógica.

### 4.3 Detección estadística
La detección basada en Z-score evita umbrales fijos y se adapta al nivel de ruido.

---

## 5. Threat Model (EN)

Assumed attacker capabilities:
- JPEG recompression
- Resizing
- Cropping
- Screenshot or camera recapture

Not addressed:
- Deliberate watermark removal
- Heavy filtering
- AI-based watermark stripping

---

## 5. Modelo de amenaza (ES)

Capacidades asumidas del atacante:
- Recompresión JPEG
- Reescalado
- Recorte
- Captura de pantalla o recaptura con cámara

Fuera de alcance:
- Eliminación deliberada de la marca
- Filtrado agresivo
- Eliminación mediante modelos de IA

---

## 6. Open Questions / Future Work (EN)

- Payload encoding beyond binary presence
- Rotation and perspective correction
- Formal integration with JUMBF manifests
- Public benchmark standardization

---

## 6. Cuestiones abiertas / trabajo futuro (ES)

- Codificación de payload más allá de presencia binaria
- Corrección de rotación y perspectiva
- Integración formal con manifiestos JUMBF
- Estandarización de benchmarks públicos

---

## 7. Notes (EN)

This document is intentionally informal and may diverge from public documentation.
It exists to preserve design intent and engineering rationale.

---

## 7. Notas finales (ES)

Este documento es intencionadamente informal y puede divergir de la documentación pública.
Su objetivo es preservar la intención de diseño y el razonamiento técnico.
