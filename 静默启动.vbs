' Silent launcher: runs without console window, lives in the system tray.
' NOTE: keep this file ASCII-only. VBScript does not read UTF-8 source reliably.
Set fso = CreateObject("Scripting.FileSystemObject")
Set ws = CreateObject("WScript.Shell")
ws.CurrentDirectory = fso.GetParentFolderName(WScript.ScriptFullName)

If Not fso.FolderExists("out") Then
    MsgBox "Not compiled yet. Run build.bat first.", 48, "Steam Translator"
    WScript.Quit
End If

ws.Run "javaw -Dfile.encoding=UTF-8 -cp out com.steamzh.Main", 0, False
