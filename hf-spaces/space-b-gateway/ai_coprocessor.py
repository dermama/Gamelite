import base64
import io
import logging
from PIL import Image
import numpy as np

logger = logging.getLogger("GameHubNexus-AICoprocessor")

class AICoprocessor:
    def __init__(self):
        self.ocr_reader = None
        self.translator = None
        
    def _initialize_models(self):
        """Lazily initialize AI models to save startup time and memory."""
        if self.ocr_reader is None:
            try:
                import easyocr
                logger.info("Initializing EasyOCR reader...")
                self.ocr_reader = easyocr.Reader(['en', 'ja']) # English and Japanese support
                logger.info("EasyOCR initialized successfully.")
            except Exception as e:
                logger.error(f"Failed to initialize EasyOCR: {str(e)}")
                
        if self.translator is None:
            try:
                from transformers import pipeline
                logger.info("Initializing English to Arabic translation pipeline...")
                # Using a tiny, fast model for translation to keep resource usage low
                self.translator = pipeline("translation", model="Helsinki-NLP/opus-mt-en-ar")
                logger.info("Translation pipeline initialized successfully.")
            except Exception as e:
                logger.error(f"Failed to initialize Translation pipeline: {str(e)}")

    async def translate_screen(self, base64_image: str, target_lang: str = "ar") -> list:
        """
        Receives a base64 image (screenshot) from the phone, extracts text via OCR,
        translates it, and returns coordinates and translated text for rendering overlays.
        """
        if not base64_image:
            return []
            
        self._initialize_models()
        
        try:
            # Decode base64 image to PIL / numpy array
            image_bytes = base64.b64decode(base64_image.split(",")[-1])
            image = Image.open(io.BytesIO(image_bytes))
            img_np = np.array(image)
            
            if self.ocr_reader is None:
                return [{"text": "OCR engine loading...", "x": 0, "y": 0, "width": 100, "height": 30}]
                
            # Perform OCR: returns list of tuples: (bbox, text, confidence)
            # bbox is list of 4 coordinates: [[x1, y1], [x2, y2], [x3, y3], [x4, y4]]
            results = self.ocr_reader.readtext(img_np)
            
            if not results:
                return []
                
            translated_blocks = []
            texts_to_translate = []
            
            # Filter and prepare texts for translation
            for bbox, text, confidence in results:
                if len(text.strip()) > 1: # Ignore single character noise
                    texts_to_translate.append(text)
            
            # Translate in batch
            translated_texts = []
            if texts_to_translate and self.translator:
                try:
                    translations = self.translator(texts_to_translate)
                    translated_texts = [t['translation_text'] for t in translations]
                except Exception as e:
                    logger.error(f"Translation model error: {str(e)}")
                    # Fallback to original text if translation fails
                    translated_texts = texts_to_translate
            else:
                translated_texts = texts_to_translate
                
            # Combine translation with bbox coordinates
            text_index = 0
            for bbox, text, confidence in results:
                if len(text.strip()) > 1:
                    # Calculate bounding box dimensions
                    x_coords = [pt[0] for pt in bbox]
                    y_coords = [pt[1] for pt in bbox]
                    
                    x_min = int(min(x_coords))
                    y_min = int(min(y_coords))
                    width = int(max(x_coords) - x_min)
                    height = int(max(y_coords) - y_min)
                    
                    arabic_text = translated_texts[text_index] if text_index < len(translated_texts) else text
                    text_index += 1
                    
                    translated_blocks.append({
                        "original_text": text,
                        "translated_text": arabic_text,
                        "x": x_min,
                        "y": y_min,
                        "width": width,
                        "height": height
                    })
                    
            return translated_blocks
            
        except Exception as e:
            logger.error(f"Error during screen translation: {str(e)}")
            return []
