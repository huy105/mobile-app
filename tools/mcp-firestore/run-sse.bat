@echo off
chcp 65001 >nul
echo ===============================================================
echo   KHOI DONG CANTEEN MCP SERVER QUA HTTP SSE (CHO PHEP DUNG URL)
echo ===============================================================

cd /d "%~dp0"

if not exist "dist\index.js" (
    echo Dang bien dich TypeScript...
    call npm run build
)

set DATA_SOURCE=fixture
set TIMEZONE=Asia/Ho_Chi_Minh
set MASK_PII=true
set PORT=3000

echo.
echo Server MCP dang lang nghe tai: http://localhost:3000/sse
echo Ban co the cau hinh MCP client su dung URL tren!
echo Nhan Ctrl+C de dung server.
echo.

node dist/index.js --sse
pause

