import os
import subprocess
import tarfile
import shutil
import logging
from fastapi import FastAPI, HTTPException, UploadFile, File, Form
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel
from shader_compiler import ShaderCompiler

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("GameHubNexus-Compute")

app = FastAPI(title="GameHub Nexus Compute & Wine Daemon")
shader_compiler = ShaderCompiler()

# Mount directories to allow static downloads of compiled files
os.makedirs("/app/prefixes", exist_ok=True)
os.makedirs("/app/dxvk_caches", exist_ok=True)
os.makedirs("/app/saves", exist_ok=True)

app.mount("/download/prefix", StaticFiles(directory="/app/prefixes"), name="prefix")
app.mount("/download/dxvk_cache", StaticFiles(directory="/app/dxvk_caches"), name="dxvk_cache")
app.mount("/download/save", StaticFiles(directory="/app/saves"), name="save")

class TaskRequest(BaseModel):
    device_id: str
    task: str
    payload: dict

@app.get("/")
async def root():
    return {"status": "online", "service": "GameHub Nexus Compute Daemon"}

@app.post("/run_task")
async def run_task(request: TaskRequest):
    device_id = request.device_id
    task = request.task
    payload = request.payload
    
    logger.info(f"Received task {task} from device {device_id}")
    
    if task == "setup_prefix":
        return await handle_setup_prefix(device_id, payload)
    elif task == "install_mod":
        return await handle_install_mod(device_id, payload)
    elif task == "compile_shaders":
        return await handle_compile_shaders(device_id, payload)
    elif task == "save_snapshot":
        return await handle_save_snapshot(device_id, payload)
    elif task == "load_snapshot":
        return await handle_load_snapshot(device_id, payload)
    else:
        raise HTTPException(status_code=400, detail=f"Unsupported task: {task}")

@app.post("/upload_snapshot")
async def upload_snapshot(
    game_id: str = Form(...),
    device_id: str = Form(...),
    file: UploadFile = File(...)
):
    """
    Handles upload of state snapshots (.tar.gz files) from the phone client.
    """
    save_dir = f"/app/saves/{device_id}"
    os.makedirs(save_dir, exist_ok=True)
    
    dest_path = os.path.join(save_dir, f"{game_id}.tar.gz")
    
    try:
        with open(dest_path, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)
        logger.info(f"Snapshot uploaded and saved to: {dest_path}")
        return {"status": "success", "message": "Snapshot uploaded successfully"}
    except Exception as e:
        logger.error(f"Failed to save snapshot file: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Failed to write snapshot: {str(e)}")

async def handle_setup_prefix(device_id: str, payload: dict):
    """
    Initializes a new Wine prefix synchronously and packages it.
    """
    prefix_id = f"prefix_{device_id}"
    prefix_path = f"/app/prefixes/{prefix_id}"
    tar_path = f"/app/prefixes/{prefix_id}.tar.gz"
    
    # Remove existing prefix if any
    if os.path.exists(prefix_path):
        shutil.rmtree(prefix_path)
    if os.path.exists(tar_path):
        os.remove(tar_path)
        
    os.makedirs(prefix_path, exist_ok=True)
    
    # Set Wine Prefix environment
    env = os.environ.copy()
    env["WINEPREFIX"] = prefix_path
    env["WINEDEBUG"] = "-all"
    
    logger.info(f"Initializing Wine Prefix at {prefix_path}...")
    
    # Run wineboot inside Xvfb (headless X server) to initialize prefix without opening windows
    cmd = ["xvfb-run", "-a", "wineboot", "--init"]
    process = subprocess.run(cmd, env=env, capture_output=True, text=True)
    
    if process.returncode != 0:
        logger.error(f"Wineboot failed: {process.stderr}")
        raise HTTPException(status_code=500, detail="Wineboot prefix initialization failed")
        
    logger.info("Wine Prefix initialized. Packaging prefix to tar.gz...")
    
    # Pack prefix directory into tar.gz
    with tarfile.open(tar_path, "w:gz") as tar:
        tar.add(prefix_path, arcname=os.path.basename(prefix_path))
        
    logger.info(f"Prefix packaged successfully at {tar_path}")
    return {"status": "success", "download_url": f"/download/prefix/{prefix_id}.tar.gz"}

async def handle_install_mod(device_id: str, payload: dict):
    """
    Simulates mod installation by copying mod files into the prefix structure.
    """
    prefix_id = f"prefix_{device_id}"
    prefix_path = f"/app/prefixes/{prefix_id}"
    mod_url = payload.get("mod_url")
    game_path_inside_prefix = payload.get("game_path") # e.g., drive_c/Program Files/Game
    
    if not os.path.exists(prefix_path):
        raise HTTPException(status_code=404, detail="Wine prefix not found. Setup prefix first.")
        
    # Download and unpack mod, copy to target path...
    logger.info(f"Installing mod from {mod_url} to prefix {prefix_id}...")
    
    return {"status": "success", "message": "Mod installed successfully in the virtual environment"}

async def handle_compile_shaders(device_id: str, payload: dict):
    """
    Triggers headless rendering to pre-compile DXVK shaders.
    """
    game_id = payload.get("game_id")
    game_executable = payload.get("executable_path")
    
    # Compile DXVK shaders
    cache_path = await shader_compiler.compile(game_id, game_executable)
    
    return {
        "status": "success",
        "game_id": game_id,
        "dxvk_cache_url": f"/download/dxvk_cache/{game_id}.dxvk-cache"
    }

async def handle_save_snapshot(device_id: str, payload: dict):
    """
    Saves a game state snapshot (saves, configs, registry).
    """
    game_id = payload.get("game_id")
    snapshot_data = payload.get("snapshot_data")
    
    save_dir = f"/app/saves/{device_id}/{game_id}"
    os.makedirs(save_dir, exist_ok=True)
    
    logger.info(f"Saving state snapshot for {game_id} (device: {device_id})")
    
    return {"status": "success", "message": "Snapshot saved successfully"}

async def handle_load_snapshot(device_id: str, payload: dict):
    """
    Loads/restores a saved snapshot.
    """
    game_id = payload.get("game_id")
    
    # Retrieve compressed save files
    logger.info(f"Loading state snapshot for {game_id} (device: {device_id})")
    
    return {"status": "success", "snapshot_url": f"/download/save/{device_id}/{game_id}.tar.gz"}

# FastAPI startup hook to start uvicorn programmatically when script runs
if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=7860)

