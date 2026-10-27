@echo off
:: MathWizard launcher for Windows (PowerShell, cmd, Windows Terminal, VS Code terminal).
:: First run: downloads the JLine jar (arrow-key menus) into lib\, then compiles and starts the game.
cd /d "%~dp0"
chcp 65001 >nul
set JAR=lib\jline-3.26.3.jar
if not exist "%JAR%" (
  if not exist lib mkdir lib
  echo Downloading JLine, needed for arrow-key menus ...
  powershell -NoProfile -Command "try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -UseBasicParsing -Uri https://repo1.maven.org/maven2/org/jline/jline/3.26.3/jline-3.26.3.jar -OutFile lib\jline-3.26.3.jar } catch { Remove-Item lib\jline-3.26.3.jar -ErrorAction SilentlyContinue }"
)
set CP=.
if exist "%JAR%" set CP=.;lib\*
if not exist "%JAR%" echo JLine not available: the game still works, with numbered menus instead of arrow keys.
javac -encoding UTF-8 -cp "%CP%" *.java && java --enable-native-access=ALL-UNNAMED -cp "%CP%" Game
pause
