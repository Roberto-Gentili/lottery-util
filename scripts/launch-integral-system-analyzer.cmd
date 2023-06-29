@echo off

call "%~dp0set-env.cmd"

set working-path.integral-system-analysis.folder=Software\config\integralSystemsAnalysis

call "%JAVA_HOME%\bin\java.exe" -Xmx5g -cp %classPath%;%LIBS%;"%CURRENT_DIR%binaries.jar"; org.rg.game.lottery.application.SEIntegralSystemAnalyzer

pause