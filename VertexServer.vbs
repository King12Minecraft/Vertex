' Vertex - hidden launcher used by VertexServer.bat
' See Vertex.vbs for why this uses "java" with a hidden window instead
' of "javaw".
Set fso = CreateObject("Scripting.FileSystemObject")
Set shell = CreateObject("WScript.Shell")
shell.CurrentDirectory = fso.GetParentFolderName(WScript.ScriptFullName)
shell.Run "java -jar VertexServer.jar", 0, False
