@echo off

set logger.type=window
call "%~dp0set-env.cmd"

call "%JAVA_HOME%\bin\native-image" -cp %classPath%;%LIBS%;"%CURRENT_DIR%binaries.jar"; org.rg.game.lottery.application.SEIntegralSystemAnalyzer