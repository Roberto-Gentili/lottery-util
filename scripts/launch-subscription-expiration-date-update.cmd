@echo off

set CURRENT_DIR=%~dp0
if "%CURRENT_DIR:~-1%" == "\" set "CURRENT_DIR_NAME_TEMP=%CURRENT_DIR:~0,-1%"

for %%f in ("%CURRENT_DIR_NAME_TEMP%") do set "CURRENT_DIR_NAME=%%~nxf"

set CURRENT_UNIT=%CURRENT_DIR:~0,2%
set JAVA_HOME=%CURRENT_DIR%jdk\20.0.1
set lottery-util.working-path=%CURRENT_DIR%..
set classPath="%CURRENT_DIR%bin"

setLocal EnableDelayedExpansion
set LIBS="
for /R "%CURRENT_DIR%lib" %%a in (*.jar) do (
	set LIBS=!LIBS!;%%a
)
set LIBS=!LIBS!"

::call  "%JAVA_HOME%\bin\java.exe" -cp %classPath%;%LIBS%;"%CURRENT_DIR%binaries.jar"; org.rg.game.lottery.application.SubscriptionExpirationDateUpdater all,1d;
::call  "%JAVA_HOME%\bin\java.exe" -cp %classPath%;%LIBS%;"%CURRENT_DIR%binaries.jar"; org.rg.game.lottery.application.SubscriptionExpirationDateUpdater Bellacanzone-Emanuele,4w;
::call  "%JAVA_HOME%\bin\java.exe" -cp %classPath%;%LIBS%;"%CURRENT_DIR%binaries.jar"; org.rg.game.lottery.application.SubscriptionExpirationDateUpdater Corinti-Massimo,1w;
::call  "%JAVA_HOME%\bin\java.exe" -cp %classPath%;%LIBS%;"%CURRENT_DIR%binaries.jar"; org.rg.game.lottery.application.SubscriptionExpirationDateUpdater Berni-Valentina,4w;
call  "%JAVA_HOME%\bin\java.exe" -cp %classPath%;%LIBS%;"%CURRENT_DIR%binaries.jar"; org.rg.game.lottery.application.SubscriptionExpirationDateUpdater
echo:
echo:
pause