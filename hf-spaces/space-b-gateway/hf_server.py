import json
import logging
import httpx
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from predictive_engine import PredictiveEngine
from ai_coprocessor import AICoprocessor

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("GameHubNexus-Gateway")

app = FastAPI(title="GameHub Nexus Gateway & AI Space")

# Enable CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Connect Space C Compiler URL (configured as environment variable or direct URL)
SPACE_C_URL = "https://your-username-space-c-compute.hf.space"  # User can configure this

# Initialize engines
predict_engine = PredictiveEngine()
ai_coprocessor = AICoprocessor()

connected_devices = {}

@app.get("/")
async def root():
    return {
        "status": "online",
        "service": "GameHub Nexus Gateway & AI Server",
        "active_devices": list(connected_devices.keys())
    }

@app.websocket("/ws/{device_id}")
async def websocket_endpoint(websocket: WebSocket, device_id: str):
    await websocket.accept()
    connected_devices[device_id] = websocket
    logger.info(f"Device connected: {device_id}")
    
    try:
        while True:
            # Receive message from phone
            data = await websocket.receive_text()
            message = json.loads(data)
            await handle_message(device_id, message, websocket)
    except WebSocketDisconnect:
        logger.info(f"Device disconnected: {device_id}")
    except Exception as e:
        logger.error(f"WebSocket error for {device_id}: {str(e)}")
    finally:
        if device_id in connected_devices:
            del connected_devices[device_id]

async def handle_message(device_id: str, message: dict, websocket: WebSocket):
    msg_type = message.get("type")
    
    if msg_type == "player_position":
        # Handle predictive prefetching
        game_id = message.get("game_id")
        x = message.get("x")
        y = message.get("y")
        map_zone = message.get("map_zone")
        
        # Run prediction engine
        prefetch_result = predict_engine.predict_next_files(game_id, x, y, map_zone)
        if prefetch_result:
            # Send prediction instruction to phone
            await websocket.send_text(json.dumps({
                "type": "prefetch_directive",
                "file_url": prefetch_result["file_url"],
                "byte_range": prefetch_result["byte_range"],
                "priority": "high"
            }))
            
    elif msg_type == "ocr_translate_request":
        # Handle real-time translation
        image_data = message.get("image_data") # Base64 encoded screenshot
        target_lang = message.get("target_lang", "ar")
        
        # Run OCR + neural translation
        translations = await ai_coprocessor.translate_screen(image_data, target_lang)
        
        await websocket.send_text(json.dumps({
            "type": "ocr_translate_response",
            "translations": translations
        }))
        
    elif msg_type == "task_forward":
        # Forward heavy compile/mod tasks to Space C (Compute Daemon)
        # We make a non-blocking request to Space C API
        task = message.get("task")
        payload = message.get("payload")
        
        logger.info(f"Forwarding task {task} to Space C")
        async with httpx.AsyncClient() as client:
            try:
                response = await client.post(f"{SPACE_C_URL}/run_task", json={
                    "device_id": device_id,
                    "task": task,
                    "payload": payload
                }, timeout=300.0)
                
                await websocket.send_text(json.dumps({
                    "type": "task_result",
                    "task": task,
                    "status": "success" if response.status_code == 200 else "failed",
                    "result": response.json()
                }))
            except Exception as e:
                logger.error(f"Failed to communicate with Space C: {str(e)}")
                await websocket.send_text(json.dumps({
                    "type": "task_result",
                    "task": task,
                    "status": "failed",
                    "error": f"Space C unreachable: {str(e)}"
                }))
    else:
        logger.warning(f"Unknown message type: {msg_type}")
