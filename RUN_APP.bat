@echo off
title Automated Banking ^& Fraud Detection Engine - Launcher
cd /d "%~dp0"
echo ============================================================
echo   Starting Banking ^& Fraud Detection Engine (JavaFX UI)
echo ============================================================
echo.

where mvn >nul 2>nul
if not errorlevel 1 (
    echo Launching via system Maven...
    call mvn javafx:run
    goto END
)

if exist "C:\Program Files\JetBrains\IntelliJ IDEA 2025.1.1.1\plugins\maven\lib\maven3\bin\mvn.cmd" (
    set "JAVA_HOME=C:\Program Files\JetBrains\IntelliJ IDEA 2025.1.1.1\jbr"
    set "PATH=C:\Program Files\JetBrains\IntelliJ IDEA 2025.1.1.1\jbr\bin;%PATH%"
    echo Launching via IntelliJ bundled Maven...
    call "C:\Program Files\JetBrains\IntelliJ IDEA 2025.1.1.1\plugins\maven\lib\maven3\bin\mvn.cmd" javafx:run
    goto END
)

echo [ERROR] Maven (mvn) was not found on your system PATH.
echo Please install Java JDK 17+ and Maven 3.8+, or add them to your system PATH.

:END
echo.
echo ============================================================
echo Application process finished. Press any key to close.
echo ============================================================
pause
