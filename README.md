# ♿ RutaAccesible IA (Asistente Urbano Multimodal)

**Proyecto:** Caso de Estudio Práctico 2 — Hackatón Nacional de Innovación con IA Aplicada 2026  
**Institución:** Universidad del Valle de México (UVM) — Ingeniería en Sistemas Computacionales  
**Alineación ODS:** ODS 10 (Reducción de Desigualdades) y ODS 11 (Ciudades y Comunidades Sostenibles)  
**Base Arquitectónica:** Adaptado de *AppSense* (Android Kotlin + Jetpack Compose + CameraX + STT/TTS + Google Gemini API)

---

## 1. Resumen de la Problemática y Solución Propuesta

* **Problemática:** Las personas con discapacidad motriz (usuarios de sillas de ruedas o muletas) y adultos mayores enfrentan graves barreras de movilidad en el entorno urbano: banquetas bloqueadas, ausencia de rampas, pasos peatonales inaccesibles o infraestructura en mal estado.
* **Solución:** **RutaAccesible IA**, una aplicación móvil asistida por IA generativa multimodal que permite capturar mediante la cámara en tiempo real o dictar por voz los obstáculos urbanos del trayecto. La aplicación procesa la imagen con **Google Gemini API (`gemini-2.5-flash`)** para evaluar el nivel de accesibilidad, detectar riesgos (Bajo, Medio, Alto) y emitir alertas sonoras inmediatas mediante síntesis de voz (TTS).

---

## 2. Arquitectura y Características Implementadas

1. **Operación Multimodal (Voz + Cámara + Botón Accesible):**
   * Hereda la arquitectura manos libres de *AppSense* (`SpeechRecognizer` + `TextToSpeech` + `CameraX`), permitiendo dictar reportes por voz o presionar un botón único de alto contraste (`Evaluar Entorno Peatonal`).
2. **Evaluación Estructurada de Accesibilidad (`gemini-2.5-flash`):**
   * Responde estrictamente con los 4 indicadores clave:
     1. ¿Cuenta con rampa? (Sí/No)
     2. Obstáculos detectados
     3. Nivel de riesgo (Bajo/Medio/Alto)
     4. Mensaje corto de advertencia auditiva
3. **Semáforo Visual y Alerta Sonora Automática:**
   * Cambia el borde y tarjeta de diagnóstico según el nivel de riesgo (**Verde** = Bajo, **Ámbar** = Medio, **Rojo** = Alto) y reproduce en voz alta la advertencia de navegación.
4. **Script Complementario en Python (`backend_python/`):**
   * Incluye la implementación de referencia en Python con el SDK oficial `google-genai` (`evaluar_accesibilidad.py`).

---

## 3. Instalación y Ejecución

### A. Aplicación Móvil Android (Android Studio)
1. Abrir la carpeta `RutaAccesibleIA` en **Android Studio**.
2. En la raíz del proyecto, crear o editar el archivo `local.properties` y agregar tu clave de Google AI Studio:
   ```properties
   GEMINI_API_KEY=tu_api_key_de_google_ai_studio
   ```
3. Sincronizar Gradle (`Sync Project with Gradle Files`) y ejecutar en un dispositivo físico Android con cámara y micrófono.

### B. Script de Validación en Python (VS Code)
```bash
cd backend_python
pip install -r requirements.txt
export GEMINI_API_KEY="tu_api_key_de_google_ai_studio"
python evaluar_accesibilidad.py
```

---

## 4. Declaración Ética del Uso de IA (Hackatón UVM 2026)

| Módulo del Proyecto | Herramienta de IA | Rol y Alcance de la IA |
| :--- | :--- | :--- |
| **Análisis de Infraestructura (`MainActivity.kt` / `evaluar_accesibilidad.py`)** | Google Gemini API (`gemini-2.5-flash`) | Detección visual de obstáculos peatonales, verificación de rampas y cálculo de riesgo de accesibilidad. |
| **Prototipado de Prompts** | Google AI Studio | Diseño y depuración de las instrucciones del sistema (*System Instructions*) en tiempo real. |
| **Documentación y Pitch** | Google Workspace (Docs / Slides) | Generación de borradores para la memoria técnica y apoyo en guion de presentación. |

---

## 5. Referencias (Formato APA 7.ª Edición)

* Google. (2026). *Google GenAI SDK Documentation and Gemini API Reference*. Google AI for Developers. https://ai.google.dev/docs
* Naciones Unidas. (2015). *Objetivos de Desarrollo Sostenible: ODS 11 Ciudades y comunidades sostenibles*. ONU. https://www.un.org/sustainabledevelopment/es/cities/
* Universidad del Valle de México. (2026). *Bases Generales y Convocatoria: Hackatón Nacional de Innovación con IA Aplicada 2026 - Ingenio Incluyente*. Dirección Nacional de Programas de Ingeniería.
