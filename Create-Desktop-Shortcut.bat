@echo off
REM Vertex - Desktop Shortcut Creator
REM
REM Run this ONCE. It creates two proper Windows shortcuts on your
REM Desktop - "Vertex.lnk" (play) and "Vertex Server.lnk" (host) - both
REM using the Vertex icon. Windows won't let you "Pin to taskbar" a
REM .bat file directly (that option is greyed out for script files),
REM but a .lnk shortcut TO one works fine. Once these exist, right-click
REM either one and choose "Pin to taskbar" (or "Show more options ->
REM Pin to taskbar" on Windows 11), or just drag it onto your taskbar.
REM
REM Uses PowerShell's WScript.Shell COM object to build the shortcuts -
REM this is the exact same standard mechanism installers use, not the
REM console-hiding trick from an earlier version of these scripts that
REM got flagged as unsafe. Creating a plain, visible .lnk file is a
REM completely different, everyday Windows operation.

set SCRIPT_DIR=%~dp0
set ICON_PATH=%SCRIPT_DIR%vertex_icon.ico

powershell -NoProfile -Command ^
  "$w = New-Object -ComObject WScript.Shell;" ^
  "$s1 = $w.CreateShortcut('%USERPROFILE%\Desktop\Vertex.lnk');" ^
  "$s1.TargetPath = '%SCRIPT_DIR%Vertex.bat';" ^
  "$s1.WorkingDirectory = '%SCRIPT_DIR%';" ^
  "$s1.IconLocation = '%ICON_PATH%';" ^
  "$s1.Description = 'Vertex - launch the game client';" ^
  "$s1.Save();" ^
  "$s2 = $w.CreateShortcut('%USERPROFILE%\Desktop\Vertex Server.lnk');" ^
  "$s2.TargetPath = '%SCRIPT_DIR%VertexServer.bat';" ^
  "$s2.WorkingDirectory = '%SCRIPT_DIR%';" ^
  "$s2.IconLocation = '%ICON_PATH%';" ^
  "$s2.Description = 'Vertex - host a server and play';" ^
  "$s2.Save()"

if exist "%USERPROFILE%\Desktop\Vertex.lnk" (
    echo.
    echo Done! Vertex and Vertex Server shortcuts were created on your Desktop.
    echo Right-click either one and choose "Pin to taskbar" to keep it there.
) else (
    echo.
    echo Something went wrong - the shortcuts weren't created.
    echo Try right-clicking this file and "Run as administrator" instead.
)
pause
