@echo off

set CURRENT_DIR=%~dp0
if "%CURRENT_DIR:~-1%" == "\" set "CURRENT_DIR_NAME_TEMP=%CURRENT_DIR:~0,-1%"

for %%f in ("%CURRENT_DIR_NAME_TEMP%") do set "CURRENT_DIR_NAME=%%~nxf"

set CURRENT_UNIT=%CURRENT_DIR:~0,2%
set JAVA_HOME=%CURRENT_DIR%jdk\19
set lottery-util.working-path=%CURRENT_DIR%..
set working-path.simulations.folder=%CURRENT_DIR_NAME%\config\simulations
set working-path.complex-simulations.folder=%CURRENT_DIR_NAME%\config\simulations
set classPath="%CURRENT_DIR%bin"

setLocal EnableDelayedExpansion
set LIBS="
for /R "%CURRENT_DIR%lib" %%a in (*.jar) do (
	set LIBS=!LIBS!;%%a
)
set LIBS=!LIBS!"


start "" /D "%~dp0" /b "%JAVA_HOME%\bin\java.exe" -cp %classPath%;%LIBS%;"%CURRENT_DIR%binaries.jar"; org.rg.game.lottery.application.SELotterySimpleSimulator
start "" /D "%~dp0" /b "%JAVA_HOME%\bin\java.exe" -cp %classPath%;%LIBS%;"%CURRENT_DIR%binaries.jar"; org.rg.game.lottery.application.SELotteryComplexSimulator