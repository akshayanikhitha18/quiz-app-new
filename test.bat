@echo off
REM Builds, then runs the checks in tests\ (no database needed)
call build.bat || exit /b 1
javac -encoding UTF-8 -cp out -d out tests\SmokeTest.java tests\HttpSmokeTest.java || exit /b 1
echo === Unit checks (hashing, grading, deadlines, duplicate submit, permissions) ===
java -cp out SmokeTest || exit /b 1
echo === HTTP checks (static files, security headers, auth, validation, error handling) ===
java -cp out HttpSmokeTest
