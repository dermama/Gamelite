# Space B: Gateway & Compute Unified Server
FROM ubuntu:22.04

# Avoid interactive prompts during apt
ENV DEBIAN_FRONTEND=noninteractive

# Enable i386 architecture for 32-bit Wine support
RUN apt-get update && apt-get install -y software-properties-common wget gnupg2 && \
    dpkg --add-architecture i386

# Install Wine, Xvfb, Python, Pip, system libraries for OpenCV/OCR
RUN apt-get update && apt-get install -y --no-install-recommends \
    wine64 \
    wine32:i386 \
    xvfb \
    x11vnc \
    p7zip-full \
    python3 \
    python3-pip \
    python3-setuptools \
    git \
    libglib2.0-0 \
    libgl1-mesa-glx \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy requirements
COPY requirements.txt .

# Install PyTorch CPU first to prevent massive CUDA wheels from exhausting space
RUN pip install --no-cache-dir torch torchvision --index-url https://download.pytorch.org/whl/cpu
RUN pip install --no-cache-dir -r requirements.txt

# Copy source code
COPY . .

# Create directories for Wine prefixes, caches, and saves
RUN mkdir -p /app/prefixes /app/saves /app/dxvk_caches

# Expose HF Port 7860
EXPOSE 7860

# Start FastAPI server
CMD ["python3", "hf_server.py"]
