@echo off
setlocal
cd /d "%~dp0"
title SCP Potions - Forge 1.20.1 Source Build

echo ================================================
echo    SCP POTIONS - SOURCE BUILD (NOT GAME INSTALL)
echo ================================================
echo.
echo This script uses the OFFICIAL Forge 47.4.20 MDK.
echo It creates build files here, outside your Minecraft modpack.
echo It DOES NOT modify Prism Launcher or your saves.
echo.

where powershell.exe >nul 2>nul
if errorlevel 1 (
  echo ERROR: PowerShell is needed to download and extract the official Forge MDK.
  goto :failed
)

if not exist ".forge-build\gradlew.bat" (
  if not exist ".forge-build" mkdir ".forge-build"
  if not exist ".forge-build\forge-mdk.zip" (
    echo Downloading official Forge 1.20.1 47.4.20 MDK...
    powershell.exe -NoProfile -Command "$ProgressPreference='SilentlyContinue'; try { Invoke-WebRequest -Uri 'https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.20/forge-1.20.1-47.4.20-mdk.zip' -OutFile '.forge-build/forge-mdk.zip'; exit 0 } catch { Write-Error $_; exit 1 }"
    if errorlevel 1 (
      echo ERROR: MDK download failed. You can download it manually using README.md.
      goto :failed
    )
  )
  echo Verifying Forge MDK SHA-1 from the official Forge downloads page...
  powershell.exe -NoProfile -Command "$hash=(Get-FileHash '.forge-build/forge-mdk.zip' -Algorithm SHA1).Hash.ToLowerInvariant(); if ($hash -ne 'ae89b7adec05802fb805c9345b509029c6952ebb') { Write-Error ('Unexpected MDK hash: ' + $hash); exit 1 }"
  if errorlevel 1 (
    echo ERROR: File failed the checksum check. Do not build from this download.
    goto :failed
  )
  echo Unpacking Forge MDK...
  powershell.exe -NoProfile -Command "Expand-Archive -Path '.forge-build/forge-mdk.zip' -DestinationPath '.forge-build' -Force"
  if errorlevel 1 goto :failed
)

if not exist ".forge-build\gradlew.bat" (
  echo ERROR: gradlew.bat missing from MDK.
  goto :failed
)

echo Preparing SCP Potions source...
if exist ".forge-build\src\main\java\com\example" rmdir /S /Q ".forge-build\src\main\java\com\example"
if exist ".forge-build\src\main\resources\assets\examplemod" rmdir /S /Q ".forge-build\src\main\resources\assets\examplemod"
xcopy "src" ".forge-build\src" /E /I /Y >nul
if errorlevel 1 goto :failed
copy /Y "build.gradle" ".forge-build\build.gradle" >nul
copy /Y "gradle.properties" ".forge-build\gradle.properties" >nul
copy /Y "settings.gradle" ".forge-build\settings.gradle" >nul
if errorlevel 1 goto :failed

echo.
echo Starting Forge build. Java 17 JDK and Internet are required.
echo.
pushd ".forge-build"
call gradlew.bat build --no-daemon
if errorlevel 1 (
  popd
  echo.
  echo ERROR: Forge Gradle reported a build failure; nothing was installed.
  echo Please copy the FIRST error section from the console output.
  goto :failed
)
popd

if not exist "dist" mkdir "dist"
copy /Y ".forge-build\build\libs\scp_potions-0.1.15-alpha.2.jar" "dist\" >nul
if errorlevel 1 goto :failed

echo.
echo SUCCESS: Check dist\scp_potions-0.1.15-alpha.2.jar
echo Test only in a COPY of your Prism instance, not your main world.
echo.
pause
exit /b 0

:failed
echo.
echo BUILD NOT COMPLETED. Your Minecraft installation was not changed.
echo.
pause
exit /b 1
