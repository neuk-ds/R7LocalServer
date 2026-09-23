#define AppName "R7 Local Server"
#ifndef AppVersion
#define AppVersion "0.0.0"
#endif
#define AppPublisher "Дмитрий Неук"
#define AppExeName "R7LocalServer.exe"
#define AppSourceDir "..\build\compose\binaries\main\app\R7LocalServer"
#define PluginsSourceDir "..\..\plugins"
#define R7PluginsDir "{localappdata}\R7-Office\Editors\data\sdkjs-plugins"
#define MacrosSyncDir R7PluginsDir + "\{{9c4be932-7357-468b-bfe1-e3a379079d87}"
#define MacrosSyncCompanionDir R7PluginsDir + "\{{dbba06c0-1008-4672-a3c3-a22215388e8d}"

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
; R7 Office registers plugins itself. Update only folders that already exist.
Source: "{#PluginsSourceDir}\Macros Sync\*"; DestDir: "{#MacrosSyncDir}"; Flags: recursesubdirs createallsubdirs ignoreversion uninsneveruninstall; Check: DirExists(ExpandConstant('{#MacrosSyncDir}'))
Source: "{#PluginsSourceDir}\Macros Sync Companion\*"; DestDir: "{#MacrosSyncCompanionDir}"; Flags: recursesubdirs createallsubdirs ignoreversion uninsneveruninstall; Check: DirExists(ExpandConstant('{#MacrosSyncCompanionDir}'))

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
Type: files; Name: "{#MacrosSyncDir}\sync-core.js"

[Run]
Filename: "{app}\{#AppExeName}"; Description: "Запустить {#AppName}"; Flags: nowait postinstall skipifsilent
