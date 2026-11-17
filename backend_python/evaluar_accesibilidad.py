import os
from google import genai
from google.genai import types
from PIL import Image

# Configuración del cliente oficial de Gemini (Paso 2 - Caso de Estudio 2 UVM 2026)
client = genai.Client(api_key=os.getenv("GEMINI_API_KEY"))

SYSTEM_PROMPT = (
    "Eres un experto en accesibilidad urbana y diseño universal. Analiza la "
    "imagen del entorno peatonal recibida e identifica: 1) Si hay accesibilidad "
    "para sillas de ruedas, 2) Obstáculos presentes (baches, autos mal estacionados, "
    "falta de rampa), 3) Un nivel de riesgo (Bajo, Medio, Alto) y 4) Una "
    "recomendación breve de navegación por audio."
)

def evaluar_accesibilidad_urbana(ruta_imagen: str) -> str:
    """
    Analiza una fotografía del entorno peatonal y retorna una evaluación
    de accesibilidad y riesgos para movilidad reducida.
    """
    try:
        imagen = Image.open(ruta_imagen)
        prompt = (
            "Analiza esta infraestructura peatonal para una persona en silla de ruedas. "
            "Responde en formato estructurado: "
            "1. ¿Cuenta con rampa? (Sí/No) "
            "2. Obstáculos detectados. "
            "3. Nivel de riesgo (Bajo/Medio/Alto). "
            "4. Mensaje corto de advertencia auditiva."
        )

        response = client.models.generate_content(
            model="gemini-2.5-flash",
            contents=[imagen, prompt],
            config=types.GenerateContentConfig(
                system_instruction=SYSTEM_PROMPT,
                temperature=0.2,
            ),
        )
        return response.text
    except Exception as e:
        return f"Error en la evaluación de infraestructura: {str(e)}"

if __name__ == "__main__":
    resultado = evaluar_accesibilidad_urbana("banqueta_ejemplo.jpg")
    print("--- Diagnóstico de Accesibilidad Peatonal ---")
    print(resultado)
