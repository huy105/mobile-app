# ==========================================
# Stage 1: Build TypeScript source code
# ==========================================
FROM node:22-alpine AS builder

WORKDIR /app

# Copy dependency definitions and typescript configuration
COPY package*.json tsconfig.json ./
RUN npm ci

# Copy source code and build dist/
COPY src ./src
RUN npm run build

# ==========================================
# Stage 2: Production runtime image
# ==========================================
FROM node:22-alpine AS runner

WORKDIR /app

ENV NODE_ENV=production
ENV DATA_SOURCE=fixture
ENV TIMEZONE=Asia/Ho_Chi_Minh
ENV MASK_PII=true

# Install only production dependencies
COPY package*.json ./
RUN npm ci --omit=dev && npm cache clean --force

# Copy compiled files and default fixture data
COPY --from=builder /app/dist ./dist
COPY fixtures ./fixtures

# MCP communicates via standard I/O (stdio)
ENTRYPOINT ["node", "dist/index.js"]

