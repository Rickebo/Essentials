:: copy /Y "C:\Projects\Java\Essentials\Essentials\target\EssentialsX-2.17.2.77.jar" "C:\SERVER\mcdev\plugins\EssentialsX-2.17.2.77.jar"

:: exit 0

@echo off
SET targetpath="C:\SERVER\mcdev\plugins\essentials.jar"
SET compilepath="C:\Projects\Java\Essentials\Essentials\target"
SET prev_cd=%CD%

cd %compilepath%

for /f "delims=" %%x in ('dir /od /b *.jar') do set latestjar=%%x
SET latestjar=%CD%\%latestjar%
cd %prev_cd%

echo Copying file
echo     FROM: %latestjar% 
echo       TO: %targetpath%...
copy /Y %latestjar% %targetpath%

pause
exit 0