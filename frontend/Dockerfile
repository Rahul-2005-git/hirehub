# =============================================================================
# Stage 1: Build React SPA bundle
# =============================================================================
FROM node:22-alpine AS builder

WORKDIR /app

# Cache package dependencies
COPY package*.json ./
RUN npm install

# Copy source and build production bundle
COPY . .
RUN npm run build

# =============================================================================
# Stage 2: Serve using optimized Nginx
# =============================================================================
FROM nginx:1.27-alpine AS runtime

# Remove default nginx config
RUN rm /etc/nginx/conf.d/default.conf

# Copy custom Nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy static assets from builder stage
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
