@echo off
chcp 65001 >nul
title Ositos Fitness - Subir backend de IA
cd /d "%~dp0"

echo.
echo  ===============================================
echo     Ositos Fitness - Subir el backend de la IA
echo  ===============================================
echo.

where node >nul 2>nul
if errorlevel 1 goto :sin_node

echo [1/5] Instalando la herramienta de Firebase (tarda 1-2 minutos)...
call npm install -g firebase-tools
if errorlevel 1 goto :error

echo.
echo [2/5] Iniciar sesion: se abre el navegador. Entra con la MISMA cuenta de Google de Firebase.
echo       Si aca te pregunta algo, responde n y Enter.
call firebase login
if errorlevel 1 goto :error

echo.
echo [3/5] Pega tu API key de Anthropic (empieza con sk-ant-) con CLIC DERECHO y apreta Enter.
echo       No se ve mientras la pegas: es normal. Si pregunta algo, responde y y Enter.
call firebase functions:secrets:set ANTHROPIC_API_KEY --project ositos-fitness
if errorlevel 1 goto :error

echo.
echo [4/5] Instalando dependencias del backend...
pushd functions
call npm ci
if errorlevel 1 (
  popd
  goto :error
)
popd

echo.
echo [5/5] Subiendo el backend y las reglas (tarda 3-5 minutos)...
call firebase deploy --only functions,firestore:rules --project ositos-fitness --force
if not errorlevel 1 goto :listo

echo.
echo El primer intento fallo. En proyectos nuevos es normal: Google tarda en habilitar permisos.
echo Reintentando en 90 segundos...
timeout /t 90 /nobreak >nul
call firebase deploy --only functions,firestore:rules --project ositos-fitness --force
if errorlevel 1 goto :error

:listo
echo.
echo  ===============================================
echo    LISTO! La foto con IA ya funciona en la app.
echo  ===============================================
echo.
pause
exit /b 0

:sin_node
echo Falta instalar Node.js. Se abre la pagina: descarga la version LTS, instalala
echo con Siguiente en todo, y despues volve a hacer doble clic en este archivo.
start https://nodejs.org
pause
exit /b 1

:error
echo.
echo  Algo fallo. Saca una captura de esta ventana y mandasela a Claude.
echo.
pause
exit /b 1
