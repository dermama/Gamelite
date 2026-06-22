# Space C: Compute & Wine Daemon
FROM ubuntu:22.04

# Avoid interactive prompts during apt
ENV DEBIAN_FRONTEND=noninteractive

# Enable i386 architecture for 32-bit Wine support
RUN apt-get update && apt-get install -y software-properties-common wget gnupg2 && \
    dpkg --add-architecture i386

# Install Wine, Xvfb (X Virtual Framebuffer for headless GUI), Python, pip, and utilities
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
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy requirements
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy source code
COPY . .

# Create directory for Wine prefixes and game saves
RUN mkdir -p /app/prefixes /app/saves /app/dxvk_caches

# Expose HF Port 7860
EXPOSE 7860

# Start script that initializes Xvfb and runs FastAPI
CMD ["python3", "task_processor.py"]
