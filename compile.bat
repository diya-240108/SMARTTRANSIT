@echo off
echo ==================================================================
echo   Compiling Smart Transit Management System...
echo ==================================================================
if not exist "bin" mkdir bin
javac -d bin -cp "lib/*" src/main/java/com/smarttransit/*.java
if %ERRORLEVEL% EQU 0 (
    echo [OK] Compilation successful! All class files generated in bin/
) else (
    echo [ERROR] Compilation failed. Please ensure JDK 21 is in PATH.
)
pause
