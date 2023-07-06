call "%~dp0set-env.cmd"

set working-path.simulations.folder=%CURRENT_DIR_NAME%\config\simulations
set working-path.complex-simulations.folder=%CURRENT_DIR_NAME%\config\simulations
::set tasks.max-parallel=10


if [%forceMaster%]==[true] (
	set firstWindowTile=SE Lottery simple simulator ^(master mode^)
	set secondWindowTile=SE Lottery complex simulator ^(master mode^)
) else (
	set firstWindowTile=SE Lottery simple simulator ^(slave mode^)
	set secondWindowTile=SE Lottery complex simulator ^(slave mode^)
)

start "%firstWindowTile%" /D "%~dp0" "%JAVA_HOME%\bin\java.exe" -Xmx%XMX% -cp %classPath%;%LIBS%;"%CURRENT_DIR%binaries.jar"; org.rg.game.lottery.application.SELotterySimpleSimulator
start "%secondWindowTile%" /D "%~dp0" "%JAVA_HOME%\bin\java.exe" -Xmx%XMX% -cp %classPath%;%LIBS%;"%CURRENT_DIR%binaries.jar"; org.rg.game.lottery.application.SELotteryComplexSimulator

::I comandi sottostanti anzichè aprire più finestre eseguono tutto nella finestra corrente
::start "%firstWindowTile%" /D "%~dp0" /b "%JAVA_HOME%\bin\java.exe" -Xmx%XMX% -cp %classPath%;%LIBS%;"%CURRENT_DIR%binaries.jar"; org.rg.game.lottery.application.SELotterySimpleSimulator
::start "%secondWindowTile%" /D "%~dp0" /b "%JAVA_HOME%\bin\java.exe" -Xmx%XMX% -cp %classPath%;%LIBS%;"%CURRENT_DIR%binaries.jar"; org.rg.game.lottery.application.SELotteryComplexSimulator