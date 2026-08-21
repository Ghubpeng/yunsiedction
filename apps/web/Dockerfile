# ============================================================================
# yunsie-web 生产镜像（多阶段构建）
#   - 阶段1：Node 24 构建（npm ci + npm run build：tsc strict + vite 产物）
#   - 阶段2：nginx 静态服务 + /api/v1 反向代理 + SPA history fallback
# ============================================================================

FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Demo 入口为构建期开关（compose 按 YUNSIE_DEMO_ENABLED 注入；默认 false = 生产无 Demo 入口）
ARG VITE_DEMO_ENABLED=false
ENV VITE_DEMO_ENABLED=$VITE_DEMO_ENABLED
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
