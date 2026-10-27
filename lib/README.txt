JLine (terminal library) lives here.

The game uses JLine for arrow-key menus on Windows (PowerShell, cmd, Windows Terminal, VS Code), Linux, macOS and Termux.
run.sh / run.bat download it automatically on the first run. To add it by hand, save this file in this folder:

  https://repo1.maven.org/maven2/org/jline/jline/3.26.3/jline-3.26.3.jar

Maven coordinates (if you move the project to a build tool): org.jline:jline:3.26.3

Without the jar the game still compiles and runs; menus then show numbers and you type the number + Enter.
