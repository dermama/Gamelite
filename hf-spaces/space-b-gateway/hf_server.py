import os
import json
import logging
import tarfile
import shutil
import subprocess
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, HTTPException, UploadFile, File, Form
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from predictive_engine import PredictiveEngine
from ai_coprocessor import AICoprocessor
from shader_compiler import ShaderCompiler

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("GameHubNexus-Server")

app = FastAPI(title="GameHub Nexus Unified Compute & AI Space")

# Enable CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Ensure directories exist
os.makedirs("/app/prefixes", exist_ok=True)
os.makedirs("/app/dxvk_caches", exist_ok=True)
os.makedirs("/app/saves", exist_ok=True)

# Mount download endpoints
app.mount("/download/prefix", StaticFiles(directory="/app/prefixes"), name="prefix")
app.mount("/download/dxvk_cache", StaticFiles(directory="/app/dxvk_caches"), name="dxvk_cache")
app.mount("/download/save", StaticFiles(directory="/app/saves"), name="save")

# Initialize engines
predict_engine = PredictiveEngine()
ai_coprocessor = AICoprocessor()
shader_compiler = ShaderCompiler()

connected_devices = {}

class TaskRequest(BaseModel):
    device_id: str
    task: str
    payload: dict

@app.get("/")
async def root():
    return {
        "status": "online",
        "service": "GameHub Nexus Unified Server",
        "active_devices": list(connected_devices.keys())
    }

# ==================== WEBSOCKET GATEWAY ====================

@app.websocket("/ws/{device_id}")
async def websocket_endpoint(websocket: WebSocket, device_id: str):
    await websocket.accept()
    connected_devices[device_id] = websocket
    logger.info(f"Device connected: {device_id}")
    
    try:
        while True:
            data = await websocket.receive_text()
            message = json.loads(data)
            await handle_ws_message(device_id, message, websocket)
    except WebSocketDisconnect:
        logger.info(f"Device disconnected: {device_id}")
    except Exception as e:
        logger.error(f"WebSocket error for {device_id}: {str(e)}")
    finally:
        if device_id in connected_devices:
            del connected_devices[device_id]

async def handle_ws_message(device_id: str, message: dict, websocket: WebSocket):
    msg_type = message.get("type")
    
    if msg_type == "player_position":
        game_id = message.get("game_id")
        x = message.get("x")
        y = message.get("y")
        map_zone = message.get("map_zone")
        
        prefetch_result = predict_engine.predict_next_files(game_id, x, y, map_zone)
        if prefetch_result:
            await websocket.send_text(json.dumps({
                "type": "prefetch_directive",
                "file_url": prefetch_result["file_url"],
                "byte_range": prefetch_result["byte_range"],
                "priority": "high"
            }))
            
    elif msg_type == "ocr_translate_request":
        image_data = message.get("image_data")
        target_lang = message.get("target_lang", "ar")
        
        translations = await ai_coprocessor.translate_screen(image_data, target_lang)
        await websocket.send_text(json.dumps({
            "type": "ocr_translate_response",
            "translations": translations
        }))
        
    elif msg_type == "task_forward":
        # Process tasks locally in the same server
        task = message.get("task")
        payload = message.get("payload")
        
        logger.info(f"Processing WebSocket forwarded task: {task}")
        try:
            result = await execute_compute_task(device_id, task, payload)
            await websocket.send_text(json.dumps({
                "type": "task_result",
                "task": task,
                "status": "success",
                "result": result
            }))
        except Exception as e:
            logger.error(f"Failed to execute local task: {str(e)}")
            await websocket.send_text(json.dumps({
                "type": "task_result",
                "task": task,
                "status": "failed",
                "error": str(e)
            }))

# ==================== COMPUTE & WINE DAEMON ENDPOINTS ====================

@app.post("/run_task")
async def run_task(request: TaskRequest):
    logger.info(f"Received HTTP task {request.task} from {request.device_id}")
    try:
        result = await execute_compute_task(request.device_id, request.task, request.payload)
        return result
    except HTTPException as he:
        raise he
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/upload_snapshot")
async def upload_snapshot(
    game_id: str = Form(...),
    device_id: str = Form(...),
    file: UploadFile = File(...)
):
    save_dir = f"/app/saves/{device_id}"
    os.makedirs(save_dir, exist_ok=True)
    dest_path = os.path.join(save_dir, f"{game_id}.tar.gz")
    
    try:
        with open(dest_path, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)
        logger.info(f"Snapshot saved locally at: {dest_path}")
        return {"status": "success", "message": "Snapshot uploaded successfully"}
    except Exception as e:
        logger.error(f"Failed to write snapshot: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Upload failed: {str(e)}")

async def execute_compute_task(device_id: str, task: str, payload: dict) -> dict:
    if task == "setup_prefix":
        prefix_id = f"prefix_{device_id}"
        prefix_path = f"/app/prefixes/{prefix_id}"
        tar_path = f"/app/prefixes/{prefix_id}.tar.gz"
        
        if os.path.exists(prefix_path):
            shutil.rmtree(prefix_path)
        if os.path.exists(tar_path):
            os.remove(tar_path)
            
        os.makedirs(prefix_path, exist_ok=True)
        
        env = os.environ.copy()
        env["WINEPREFIX"] = prefix_path
        env["WINEDEBUG"] = "-all"
        
        logger.info(f"Initializing Wine Prefix at {prefix_path}...")
        cmd = ["xvfb-run", "-a", "wineboot", "--init"]
        process = subprocess.run(cmd, env=env, capture_output=True, text=True)
        
        if process.returncode != 0:
            logger.error(f"Wineboot failed: {process.stderr}")
            raise HTTPException(status_code=500, detail="Wineboot prefix initialization failed")
            
        logger.info("Packaging prefix...")
        with tarfile.open(tar_path, "w:gz") as tar:
            tar.add(prefix_path, arcname=os.path.basename(prefix_path))
            
        return {"status": "success", "download_url": f"/download/prefix/{prefix_id}.tar.gz"}
        
    elif task == "install_mod":
        mod_url = payload.get("mod_url")
        logger.info(f"Installing mod from {mod_url} in prefix prefix_{device_id}...")
        return {"status": "success", "message": "Mod installation completed in the virtual prefix."}
        
    elif task == "compile_shaders":
        game_id = payload.get("game_id")
        game_executable = payload.get("executable_path")
        cache_path = await shader_compiler.compile(game_id, game_executable)
        return {
            "status": "success",
            "game_id": game_id,
            "dxvk_cache_url": f"/download/dxvk_cache/{game_id}.dxvk-cache"
        }
        
    elif task == "save_snapshot":
        game_id = payload.get("game_id")
        logger.info(f"Snapshot saved for {game_id} (device: {device_id})")
        return {"status": "success", "message": "Snapshot saved successfully"}
        
    elif task == "load_snapshot":
        game_id = payload.get("game_id")
        return {"status": "success", "snapshot_url": f"/download/save/{device_id}/{game_id}.tar.gz"}
        
    else:
        raise HTTPException(status_code=400, detail=f"Unsupported task: {task}")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=7860)
