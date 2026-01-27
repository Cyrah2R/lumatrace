# LumaTrace Core Engine

![Java](https://img.shields.io/badge/java-21-orange)
![C2PA](https://img.shields.io/badge/C2PA-Soft%20Binding-orange)
![Performance](https://img.shields.io/badge/performance-O(n)-green)

**LumaTrace Core** es la implementación de referencia del algoritmo de **Marca de Agua Espacial Adaptativa**. Esta librería mitiga la vulnerabilidad del "Analog Hole" incrustando identificadores persistentes e invisibles directamente en la señal de la imagen.

## 🧩 Algoritmo y Arquitectura

El motor utiliza un generador de ruido pseudo-aleatorio determinista sembrado por un hash criptográfico de los metadatos.

```mermaid
graph LR
    A[Source Image] --> B{Entropy Analysis};
    C[Metadata Payload] --> D[Key Derivation];
    D --> E[PRNG Signature 64x64];
    B --> F[Gain Map];
    E --> G[Signal Modulation];
    F --> G;
    G --> H[Blue Channel Injection];
    H --> I[Watermarked Asset];

Características Clave
Invisibilidad: Aprovecha la insensibilidad del Sistema Visual Humano (HVS) al ruido en el canal azul.

Robustez: Sobrevive a compresión JPEG (Q>50), reescalado (>0.5x) y recorte (Cropping).

Rendimiento: Detección optimizada O(n) usando técnicas de imagen integral (FastBitmap).

📊 Métricas de Rendimiento
Pruebas realizadas en dataset estándar (Lenna, Kodak) a resolución 1080p.

Escenario de Ataque	Sigma Promedio (σ)	Veredicto	Umbral
Nativo (Sin Ataque)	37.32	✅ PASS	> 4.0
Compresión JPEG (Q=70)	36.09	✅ PASS	> 4.0
Compresión JPEG (Q=50)	34.50	✅ PASS	> 4.0
Escalado (50%)	16.82	✅ PASS	> 4.0
Recorte Central (80%)	33.73	✅ PASS	> 4.0

💻 Uso (CLI)El artefacto se empaqueta como un JAR autónomo ("Fat Jar") listo para usar.Incrustar (Embed)Bashjava -jar lumatrace-core.jar embed --input source.jpg --output secured.jpg
Detectar (Detect)Bashjava -jar lumatrace-core.jar detect --input suspicious.jpg
Salida:Plaintext------------------------------------------------
DETECTION RESULT | Time: 142ms
------------------------------------------------
Confidence (Sigma) : 36.6042
Detected Scale     : 1.00x
VERDICT            : PASS
------------------------------------------------
🔧 ConfiguraciónCrea un archivo lumatrace.properties junto al JAR o usa variables de entorno:Properties# lumatrace.properties
master.key=0xDEADBEEF12345678
default.user=production-user
image.jpeg.quality.embed=0.95

📦 Integración como LibreríaJavaWatermarkEngine engine = new WatermarkEngine();
BufferedImage secured = engine.embedWatermark(original, key, "user", "content-id");

Maintained by the LumaTrace Open Source Project.
