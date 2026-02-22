@echo off
setlocal
set JAVA_HOME=D:\Program Files\Java\jdk-21.0.9
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d D:\ai\ai-lab\forge
gradlew.bat :services:user-service:clean :services:user-service:bootJar -x test --no-daemon
endlocal