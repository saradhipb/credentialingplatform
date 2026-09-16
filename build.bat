@echo off
REM ---------------------------------------------------------------------------
REM build.bat
REM
REM Windows equivalent of build.sh. Builds the Angular client, copies the bundle
REM into the Spring Boot service's static resources, then builds and tests the
REM service.
REM
REM Usage:  build.bat          build and test
REM         build.bat run      build, test, then start the service
REM ---------------------------------------------------------------------------
setlocal
set ROOT=%~dp0

echo ==^> Building the Angular client
cd /d "%ROOT%frontend"
call npm install || goto :error
call npm run build || goto :error

echo ==^> Copying the client bundle into the service's static resources
if exist "%ROOT%backend\src\main\resources\static" rmdir /s /q "%ROOT%backend\src\main\resources\static"
mkdir "%ROOT%backend\src\main\resources\static"
xcopy /E /I /Y "%ROOT%frontend\dist\*" "%ROOT%backend\src\main\resources\static\" >nul || goto :error

echo ==^> Building and testing the Spring Boot service
cd /d "%ROOT%backend"
call mvn clean package || goto :error

echo ==^> Build complete: backend\target\credentialing-platform.jar

if "%1"=="run" (
  echo ==^> Starting the platform on http://localhost:8080
  java -jar target\credentialing-pbulatform.jar
)
goto :eof

:error
echo.
echo BUILD FAILED. See the messages above.
exit /b 1
