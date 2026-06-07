#define AppName "R7 Local Server"
#ifndef AppVersion
#define AppVersion "0.0.0"
#endif
#define AppPublisher "Дмитрий Неук"
#define AppExeName "R7LocalServer.exe"
#define AppSourceDir "..\build\compose\binaries\main\app\R7LocalServer"

[Setup]
AppId={{3881d6da-9f4a-4b93-82d3-2ced610b5bad}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
VersionInfoCompany={#AppPublisher}
VersionInfoDescription={#AppName} Installer
VersionInfoProductName={#AppName}
VersionInfoProductVersion={#AppVersion}
DefaultDirName={localappdata}\Programs\{#AppName}
DefaultGroupName={#AppName}
OutputDir=..\build\inno
OutputBaseFilename=R7LocalServerSetup-{#AppVersion}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
SetupIconFile=..\src\jvmMain\composeResources\files\icon.ico
UninstallDisplayIcon={app}\{#AppExeName}
VersionInfoVersion={#AppVersion}
UninstallDisplayName={#AppName}
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible

[Languages]
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"

[Files]
Source: "{#AppSourceDir}\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppExeName}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExeName}"; Tasks: desktopicon
Name: "{group}\Удалить {#AppName}"; Filename: "{uninstallexe}"

[Tasks]
Name: "desktopicon"; Description: "Создать ярлык на рабочем столе"; GroupDescription: "Дополнительные значки:"; Flags: unchecked

[Registry]
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueName: "R7LocalServer"; Flags: uninsdeletevalue

[InstallDelete]
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"
Type: files; Name: "{app}\R7LocalServer.exe"

[Run]
Filename: "{app}\{#AppExeName}"; Description: "Запустить {#AppName}"; Flags: nowait postinstall skipifsilent