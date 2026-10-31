import http from "node:http";
import { URL } from "node:url";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { SSEServerTransport } from "@modelcontextprotocol/sdk/server/sse.js";
import { loadConfig } from "./config.js";

const config = loadConfig();

/**
 * Factory tạo instance McpServer và đăng ký toàn bộ tools
 */
export function createServer(): McpServer {
  const server = new McpServer({
    name: "canteen-report",
    version: "1.0.0",
  });

  // Tool tạm thời kiểm tra trạng thái
  server.tool(
    "ping",
    "Kiểm tra trạng thái máy chủ MCP",
    {},
    async () => {
      return {
        content: [
          {
            type: "text",
            text: `pong (nguồn dữ liệu: ${config.dataSource})`,
          },
        ],
      };
    }
  );

  return server;
}

/**
 * Chạy server chế độ Stdio (dành cho kết nối qua command)
 */
async function startStdio() {
  console.error(`[mcp-firestore] Khởi động stdio với DATA_SOURCE=${config.dataSource}, TIMEZONE=${config.timezone}`);
  const server = createServer();
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error("[mcp-firestore] Server đã kết nối qua stdio.");
}

/**
 * Chạy server chế độ SSE HTTP (dành cho kết nối qua URL: http://localhost:3000/sse)
 */
async function startSSE(port: number = 3000) {
  const transports = new Map<string, SSEServerTransport>();

  const httpServer = http.createServer(async (req, res) => {
    // Cho phép CORS
    res.setHeader("Access-Control-Allow-Origin", "*");
    res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    res.setHeader("Access-Control-Allow-Headers", "Content-Type");

    if (req.method === "OPTIONS") {
      res.writeHead(200);
      res.end();
      return;
    }

    const host = req.headers.host || `localhost:${port}`;
    const url = new URL(req.url || "/", `http://${host}`);

    // Endpoint bắt đầu kết nối SSE
    if (url.pathname === "/sse" && req.method === "GET") {
      const transport = new SSEServerTransport("/messages", res);
      transports.set(transport.sessionId, transport);

      const server = createServer();
      await server.connect(transport);

      transport.onclose = () => {
        transports.delete(transport.sessionId);
        console.error(`[mcp-firestore] Đã ngắt kết nối session: ${transport.sessionId}`);
      };

      console.error(`[mcp-firestore] Client mới kết nối SSE: session=${transport.sessionId}`);
      return;
    }

    // Endpoint nhận tin nhắn client gửi lên
    if (url.pathname === "/messages" && req.method === "POST") {
      const sessionId = url.searchParams.get("sessionId");
      const transport = sessionId ? transports.get(sessionId) : undefined;

      if (!transport) {
        res.writeHead(400, { "Content-Type": "text/plain" });
        res.end("Session ID không hợp lệ hoặc đã hết hạn");
        return;
      }

      await transport.handlePostMessage(req, res);
      return;
    }

    // Health check endpoint
    if (url.pathname === "/health" || url.pathname === "/") {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(
        JSON.stringify({
          status: "online",
          server: "canteen-report",
          dataSource: config.dataSource,
          sseUrl: `http://${host}/sse`,
        })
      );
      return;
    }

    res.writeHead(404, { "Content-Type": "text/plain" });
    res.end("Not Found");
  });

  httpServer.listen(port, "0.0.0.0", () => {
    console.error(`[mcp-firestore] Server HTTP SSE đang chạy tại: http://localhost:${port}/sse`);
    console.error(`[mcp-firestore] Endpoint Health Check: http://localhost:${port}/health`);
    console.error(`[mcp-firestore] Sẵn sàng nhận kết nối MCP qua URL!`);
  });
}

async function main() {
  const isSSE =
    process.argv.includes("--sse") ||
    process.env.MCP_TRANSPORT === "sse" ||
    Boolean(process.env.PORT);

  const port = process.env.PORT ? parseInt(process.env.PORT, 10) : 3000;

  if (isSSE) {
    await startSSE(port);
  } else {
    await startStdio();
  }
}

main().catch((err) => {
  console.error("[mcp-firestore] Lỗi khởi động:", err);
  process.exit(1);
});
