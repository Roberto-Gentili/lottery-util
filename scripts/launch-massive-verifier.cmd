@echo off

call "%~dp0set-env.cmd"

set endDate=next+1*1

call "%JAVA_HOME%\bin\java.exe" -cp %classPath%;%LIBS%;"%CURRENT_DIR%binaries.jar"; org.rg.game.lottery.application.SEMassiveVerifierAndQualityChecker
echo: 
echo:
pause