# Space B: WS Gateway & AI Server
FROM python:3.10-slim

WORKDIR /app

# Install system dependencies for OCR/AI (opencv-python-headless requirements)
RUN apt-get update && apt-get install -y --no-install-recommends \
    libglib2.0-0 \
    libgl1-mesa-glx \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements
COPY requirements.txt .

# Install dependencies (CPU versions of torch to keep container size low for free tier)
RUN pip install --no-cache-dir --extra-index-url https://download.pytorch.org/whl/cpu -r requirements.txt

# Copy source code
COPY . .

# Expose HF Port
EXPOSE 7860

# Start FastAPI server
CMD ["uvicorn", "hf_server:app", "--host", "0.0.0.0", "--port", "7860"]
