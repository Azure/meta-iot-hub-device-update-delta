SUMMARY = "Azure IoT Hub Device Update Delta DiffGenTool (C# .NET)"
DESCRIPTION = "C# .NET tool for generating PAMZ format delta files"
HOMEPAGE = "https://github.com/Azure/iot-hub-device-update-delta"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=d4a904ca135bb7bc912156fee12726f0"

# Inherit native to build for build host (x86_64)
inherit native

# Reuse source configuration from processor recipe
require iot-hub-device-update-delta-processor_${PV}.bb

# Override NuGet.config to use public nuget.org instead of private Microsoft feed
python do_patch:append() {
    # Replace NuGet.config with public nuget.org feed
    nuget_config = """<?xml version="1.0" encoding="utf-8"?>
<configuration>
  <packageSources>
    <clear />
    <add key="nuget.org" value="https://api.nuget.org/v3/index.json" protocolVersion="3" />
  </packageSources>
</configuration>
"""
    import os
    s_dir = d.getVar('S')
    with open(os.path.join(s_dir, 'NuGet.config'), 'w') as f:
        f.write(nuget_config)
    
    # Remove the PostBuild native library copy step that requires C++ builds
    # The C# DiffGenTool doesn't actually need these native libs
    csproj_path = os.path.join(s_dir, 'src/managed/DiffGen/tools/DiffGenTool/DiffGenTool.csproj')
    with open(csproj_path, 'r') as f:
        csproj_content = f.read()
    
    # Remove the PostBuild target that tries to copy native binaries
    csproj_content = csproj_content.replace(
        '<Target Name="PostBuild" AfterTargets="PostBuildEvent">\n    <Copy SourceFiles="@(NativeBinaries)" DestinationFolder="$(OutputPath)" />\n  </Target>',
        '<!-- PostBuild native copy disabled for Yocto build -->'
    )
    
    with open(csproj_path, 'w') as f:
        f.write(csproj_content)
}

# Check for .NET SDK at runtime
python() {
    import subprocess
    try:
        result = subprocess.run(['dotnet', '--version'], capture_output=True, text=True, check=False)
        if result.returncode != 0:
            bb.fatal("dotnet SDK not found. Please install .NET 6 or 8 SDK:\n" +
                    "  wget https://dot.net/v1/dotnet-install.sh\n" +
                    "  chmod +x dotnet-install.sh\n" +
                    "  sudo ./dotnet-install.sh --channel 8.0 --install-dir /usr/share/dotnet")
    except FileNotFoundError:
        bb.fatal("dotnet command not found in PATH. Please install .NET SDK.")
}

# No CMake needed - we use dotnet build
do_configure() {
    :
}

# Build the C# DiffGenTool using dotnet
do_compile() {
    cd ${S}/src/managed/DiffGen
    
    # Use the user's NuGet cache to avoid re-downloading packages
    export NUGET_PACKAGES="${HOME}/.nuget/packages"
    export DOTNET_CLI_HOME="${HOME}"
    
    # Restore NuGet packages (now using public nuget.org)
    /usr/bin/dotnet restore tools/DiffGenTool/DiffGenTool.csproj \
        --configfile ${S}/NuGet.config \
        || bbfatal "dotnet restore failed"
    
    # Publish as self-contained Linux x64 executable
    /usr/bin/dotnet publish tools/DiffGenTool/DiffGenTool.csproj \
        -c Release \
        -r linux-x64 \
        --self-contained true \
        -p:PublishSingleFile=true \
        -p:IncludeNativeLibrariesForSelfExtract=true \
        -p:WarningsAsErrors="" \
        -o ${B}/diffgentool-output \
        || bbfatal "dotnet publish failed"
}

# Install the DiffGenTool binary
do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${B}/diffgentool-output/DiffGenTool ${D}${bindir}/diffgentool
}

FILES:${PN} = "${bindir}/diffgentool"

# Skip QA checks for .NET binary
INSANE_SKIP:${PN} = "already-stripped ldflags"
