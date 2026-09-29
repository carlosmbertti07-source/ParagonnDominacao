@echo off
rem Compila o ParagonnDominacao usando o JDK portatil em ..\runtime (se existir)
cd /d "%~dp0"
for /d %%D in ("%~dp0..\runtime\jdk-*") do set "JAVA_HOME=%%~fD"
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"

call gradlew.bat build || ( echo [ERRO] Build falhou. & pause & exit /b 1 )
echo.
echo Jar gerado em build\libs\
pause
