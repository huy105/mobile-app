@echo off
chcp 65001 >nul
echo =======================================================
echo   KHOI DONG CANTEEN MCP SERVER (CHẾ ĐỘ FIXTURE)
echo =======================================================

cd /d "%~dp0"

if not exist "dist\index.js" (
    echo Dang bien dich TypeScript...
    call npm run build
)

set DATA_SOURCE=fixture
set TIMEZONE=Asia/Ho_Chi_Minh
set MASK_PII=true

echo Server dang chay qua stdio. Nhan Ctrl+C de dung.
node dist/index.js
pause

