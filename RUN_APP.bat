@echo off
title Automated Banking and Fraud Detection Engine - Launcher
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\JetBrains\IntelliJ IDEA 2025.1.1.1\jbr"
set "PATH=C:\Program Files\JetBrains\IntelliJ IDEA 2025.1.1.1\jbr\bin;%PATH%"
echo Launching JavaFX application...
call "C:\Program Files\JetBrains\IntelliJ IDEA 2025.1.1.1\plugins\maven\lib\maven3\bin\mvn.cmd" javafx:run
if errorlevel 1 (
    echo.
    echo Launch failed. Ensure JDK 17+ and Maven 3.8+ are on your PATH.
)
pause
