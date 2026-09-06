' Vertex - hidden launcher used by Vertex.bat
' Runs "java -jar VertexClient.jar" with its console window hidden
' (WindowStyle 0). Uses "java" rather than "javaw" on purpose - some
' setups (including BlueJ-managed PATHs) only expose java.exe, not
' javaw.exe, even though they're normally installed side by side.
' Hiding the window this way works with either one, since the hiding
' happens at the process-launch level, not inside java.exe itself.
Set fso = CreateObject("Scripting.FileSystemObject")
Set shell = CreateObject("WScript.Shell")
shell.CurrentDirectory = fso.GetParentFolderName(WScript.ScriptFullName)
shell.Run "java -jar VertexClient.jar", 0, False
