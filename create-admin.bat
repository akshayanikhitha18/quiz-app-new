@echo off
REM Creates the administrator account (password is typed at the prompt and stored only as a PBKDF2 hash)
call build.bat || exit /b 1
java -cp "out;lib\*" com.quizsphere.CreateAdmin
