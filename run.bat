@echo off
REM Starts QuizSphere at http://127.0.0.1:8000  (run from this folder; needs lib\mysql-connector-j-*.jar and a .env file)
call build.bat || exit /b 1
java -cp "out;lib\*" com.quizsphere.Main
