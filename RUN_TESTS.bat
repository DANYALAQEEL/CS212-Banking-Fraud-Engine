@echo off
title Automated Banking and Fraud Detection Engine - Test Suite
cd /d "%~dp0"
call mvn -B test
pause
