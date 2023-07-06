@echo off

call "%~dp0set-env.cmd"

::call  "%JAVA_HOME%\bin\%JAVA_COMMAND%" -cp %classPath%;%LIBS%;"%CURRENT_DIR%binaries.jar"; org.rg.game.lottery.application.SubscriptionExpirationDateUpdater all,1d;
::call  "%JAVA_HOME%\bin\%JAVA_COMMAND%" -cp %classPath%;%LIBS%;"%CURRENT_DIR%binaries.jar"; org.rg.game.lottery.application.SubscriptionExpirationDateUpdater Bellacanzone-Emanuele,4w;
::call  "%JAVA_HOME%\bin\%JAVA_COMMAND%" -cp %classPath%;%LIBS%;"%CURRENT_DIR%binaries.jar"; org.rg.game.lottery.application.SubscriptionExpirationDateUpdater Corinti-Massimo,1w;
::call  "%JAVA_HOME%\bin\%JAVA_COMMAND%" -cp %classPath%;%LIBS%;"%CURRENT_DIR%binaries.jar"; org.rg.game.lottery.application.SubscriptionExpirationDateUpdater Berni-Valentina,4w;
call  "%JAVA_HOME%\bin\%JAVA_COMMAND%" -Xmx%XMX% -cp %classPath%;%LIBS%;"%CURRENT_DIR%binaries.jar"; org.rg.game.lottery.application.SubscriptionExpirationDateUpdater
echo:
echo:
pause