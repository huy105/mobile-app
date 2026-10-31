@echo off
chcp 65001 >nul
echo =======================================================
echo   MO MCP INSPECTOR KIEM TRA TRUC QUAN TREN TRINH DUYET
echo =======================================================

cd /d "%~dp0"

echo Dang build lai code moi nhat...
call npm run build

set DATA_SOURCE=fixture
set TIMEZONE=Asia/Ho_Chi_Minh
set MASK_PII=true

echo Dang mo MCP Inspector tren browser...
npx @modelcontextprotocol/inspector node dist/index.js
pause

