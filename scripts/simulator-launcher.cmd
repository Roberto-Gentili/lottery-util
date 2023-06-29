call "%~dp0set-env.cmd"

set working-path.simulations.folder=%CURRENT_DIR_NAME%\config\simulations
set working-path.complex-simulations.folder=%CURRENT_DIR_NAME%\config\simulations
::set tasks.max-parallel=10

start "" /D "%~dp0" /b "%JAVA_HOME%\bin\java.exe" -Xmx%XMX% -cp %classPath%;%LIBS%;"%CURRENT_DIR%binaries.jar"; org.rg.game.lottery.application.SELotterySimpleSimulator
start "" /D "%~dp0" /b "%JAVA_HOME%\bin\java.exe" -Xmx%XMX% -cp %classPath%;%LIBS%;"%CURRENT_DIR%binaries.jar"; org.rg.game.lottery.application.SELotteryComplexSimulator