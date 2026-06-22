# Space A: Storage & CDN Server
FROM alpine:latest

# Install nginx
RUN apk add --no-cache nginx

# Create directories
WORKDIR /app
RUN mkdir -p /app/games /run/nginx

# Copy nginx config
COPY nginx.conf /etc/nginx/nginx.conf

# Expose port 7860 (Hugging Face default port)
EXPOSE 7860

# Start Nginx
CMD ["nginx", "-g", "daemon off;"]
