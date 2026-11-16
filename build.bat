@echo off
REM Compiles the backend (needs JDK 17 or newer: javac -version)
if not exist out mkdir out
dir /s /b backend\src\*.java > out\sources.txt
javac -encoding UTF-8 -d out @out\sources.txt
if errorlevel 1 (echo BUILD FAILED & exit /b 1)
echo BUILD OK
