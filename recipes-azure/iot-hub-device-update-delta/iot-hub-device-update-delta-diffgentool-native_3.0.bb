SUMMARY = "Azure IoT Hub Device Update Delta DiffGenTool (C# .NET)"
DESCRIPTION = "C# .NET tool for generating PAMZ format delta files"
HOMEPAGE = "https://github.com/Azure/iot-hub-device-update-delta"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=d4a904ca135bb7bc912156fee12726f0"

# Inherit native to build for build host (x86_64)
inherit native

# Source configuration - use same as processor recipe
SRC_URI = "${ADU_DELTA_SRC_URI};protocol=https;branch=${ADU_DELTA_GIT_BRANCH}"
SRCREV = "${AUTOREV}"

S = "${WORKDIR}/git"
B = "${WORKDIR}/build"

# Runtime dependencies - none for native recipe
RDEPENDS:${PN} = ""

# Configure NuGet package source
# Priority: NUGET_CONFIG_PATH (file) > NUGET_SOURCE (variable) > nuget.org (default)
python do_patch:append() {
    import os
    import shutil

    s_dir = d.getVar('S')
    nuget_config_path = d.getVar('NUGET_CONFIG_PATH')
    nuget_source = d.getVar('NUGET_SOURCE')

    # Option 1: Use centralized NuGet.config from NUGET_CONFIG_PATH (pipeline builds)
    if nuget_config_path and os.path.exists(nuget_config_path):
        bb.note(f"Using centralized NuGet.config from: {nuget_config_path}")
        shutil.copy(nuget_config_path, os.path.join(s_dir, 'NuGet.config'))
    # Option 2: Generate config based on NUGET_SOURCE variable (local builds)
    elif nuget_source:
        if nuget_source == 'microsoft-internal':
            bb.note("Using Microsoft internal NuGet feed (ADU-Diffs)")
            nuget_config = """<?xml version="1.0" encoding="utf-8"?>
<configuration>
  <packageSources>
    <clear />
    <add key="ADU-Diffs" value="https://microsoft.pkgs.visualstudio.com/OS.Fun/_packaging/ADU-Diffs/nuget/v3/index.json" />
  </packageSources>
</configuration>
"""
        elif nuget_source == 'nuget-org':
            bb.note("Using public nuget.org feed")
            nuget_config = """<?xml version="1.0" encoding="utf-8"?>
<configuration>
  <packageSources>
    <clear />
    <add key="nuget.org" value="https://api.nuget.org/v3/index.json" protocolVersion="3" />
  </packageSources>
</configuration>
"""
        else:
            bb.note(f"Using custom NuGet source: {nuget_source}")
            nuget_config = f"""<?xml version="1.0" encoding="utf-8"?>
<configuration>
  <packageSources>
    <clear />
    <add key="custom" value="{nuget_source}" protocolVersion="3" />
  </packageSources>
</configuration>
"""
        with open(os.path.join(s_dir, 'NuGet.config'), 'w') as f:
            f.write(nuget_config)
    # Option 3: Default to public nuget.org
    else:
        bb.note("Using default public nuget.org feed")
        nuget_config = """<?xml version="1.0" encoding="utf-8"?>
<configuration>
  <packageSources>
    <clear />
    <add key="nuget.org" value="https://api.nuget.org/v3/index.json" protocolVersion="3" />
  </packageSources>
</configuration>
"""
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

# Enable network access for do_compile since dotnet restore needs to download NuGet packages
# This is required when the NuGet cache is empty or packages need to be updated
do_compile[network] = "1"

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
    # Disable NuGetAudit to avoid network errors in BitBake sandbox (NU1900)
    /usr/bin/dotnet restore tools/DiffGenTool/DiffGenTool.csproj \
        --configfile ${S}/NuGet.config \
        -p:NuGetAudit=false \
        || bbfatal "dotnet restore failed"

    # Publish as self-contained Linux x64 executable
    # Disable NuGetAudit to avoid network errors in BitBake sandbox (NU1900)
    # Suppress IL3000 warning about Assembly.Location in single-file apps
    # Disable StyleCop analyzers completely - code style not critical for build
    /usr/bin/dotnet publish tools/DiffGenTool/DiffGenTool.csproj \
        -c Release \
        -r linux-x64 \
        --self-contained true \
        -p:PublishSingleFile=true \
        -p:IncludeNativeLibrariesForSelfExtract=true \
        -p:NuGetAudit=false \
        -p:EnableTrimAnalyzer=false \
        -p:EnableAnalyzers=false \
        -p:RunAnalyzersDuringBuild=false \
        -p:RunAnalyzersDuringLiveAnalysis=false \
        -p:EnforceCodeStyleInBuild=false \
        -p:TreatWarningsAsErrors=false \
        -p:WarningsAsErrors= \
        -o ${B}/diffgentool-output \
        || bbfatal "dotnet publish failed"
}

# Install the DiffGenTool binary and create a wrapper script
do_install() {
    install -d ${D}${bindir}
    install -d ${D}${libdir}

    # Install the actual DiffGenTool binary with a different name
    install -m 0755 ${B}/diffgentool-output/DiffGenTool ${D}${bindir}/diffgentool.bin

    # Install/symlink required libraries to ${D}${libdir} so they're in LD_LIBRARY_PATH
    # This allows diffgentool.bin --help to work, which is needed to detect the .NET extraction directory

    # Find and install jsoncpp library
    JSONCPP_LIB=$(find ${RECIPE_SYSROOT_NATIVE} -name "libjsoncpp.so.25" 2>/dev/null | head -1)
    if [ -n "$JSONCPP_LIB" ]; then
        install -m 0755 "$JSONCPP_LIB" ${D}${libdir}/
        bbnote "Installed libjsoncpp.so.25 to ${D}${libdir}"
    else
        bbwarn "Could not find libjsoncpp.so.25 in sysroot"
    fi

    # Find jsoncpp and processor library directories for wrapper script
    JSONCPP_LIBDIR=$(find ${RECIPE_SYSROOT_NATIVE} -type d -path "*/jsoncpp-native/usr/lib" 2>/dev/null | head -1)
    [ -z "$JSONCPP_LIBDIR" ] && JSONCPP_LIBDIR="\${SCRIPT_DIR}/../lib"

    # Find processor library path at build time
    PROCESSOR_LIBDIR=$(find ${RECIPE_SYSROOT_NATIVE} -type d -path "*/iot-hub-device-update-delta-processor-native/usr/lib" 2>/dev/null | head -1)
    [ -z "$PROCESSOR_LIBDIR" ] && PROCESSOR_LIBDIR="\${SCRIPT_DIR}/../lib"

    # Create wrapper script with hardcoded library paths
    cat > ${D}${bindir}/diffgentool <<EOF
#!/bin/bash
# Wrapper script for DiffGenTool that sets up required helper binaries

# Set up library path with hardcoded paths from build time
SCRIPT_DIR="\$( cd "\$( dirname "\${BASH_SOURCE[0]}" )" && pwd )"
export LD_LIBRARY_PATH="${PROCESSOR_LIBDIR}:${JSONCPP_LIBDIR}:\${LD_LIBRARY_PATH}"

# Detect the .NET extraction directory by running DiffGenTool briefly
RUNNING_DIR=\$("\${SCRIPT_DIR}/diffgentool.bin" --help 2>&1 | grep "Running Directory:" | awk '{print \$3}')

if [ -n "\$RUNNING_DIR" ] && [ -d "\$RUNNING_DIR" ]; then
    # Symlink helper binaries into .NET extraction directory if they don't exist
    if [ ! -e "\$RUNNING_DIR/bsdiff" ]; then
        ln -sf \$(which bsdiff) "\$RUNNING_DIR/bsdiff" 2>/dev/null || true
    fi
    if [ ! -e "\$RUNNING_DIR/zstd_compress_file" ]; then
        ln -sf \$(which zstd_compress_file) "\$RUNNING_DIR/zstd_compress_file" 2>/dev/null || true
    fi
    if [ ! -e "\$RUNNING_DIR/libadudiffapi.so" ]; then
        # Find libadudiffapi.so in LD_LIBRARY_PATH or standard locations
        for dir in \${LD_LIBRARY_PATH//:/ } /usr/lib /usr/local/lib; do
            if [ -f "\$dir/libadudiffapi.so" ]; then
                ln -sf "\$dir/libadudiffapi.so" "\$RUNNING_DIR/libadudiffapi.so"
                break
            fi
        done
    fi
    if [ ! -e "\$RUNNING_DIR/dumpextfs" ]; then
        ln -sf \$(which dumpextfs) "\$RUNNING_DIR/dumpextfs" 2>/dev/null || true
    fi
    if [ ! -e "\$RUNNING_DIR/libjsoncpp.so.25" ]; then
        # Find libjsoncpp.so.25 for libadudiffapi.so transitive dependency
        for dir in \${LD_LIBRARY_PATH//:/ } /usr/lib /usr/local/lib; do
            if [ -f "\$dir/libjsoncpp.so.25" ]; then
                ln -sf "\$dir/libjsoncpp.so.25" "\$RUNNING_DIR/libjsoncpp.so.25"
                break
            fi
        done
    fi
    if [ ! -e "\$RUNNING_DIR/libzstd_compress_file.so" ]; then
        # Find libzstd_compress_file.so if it exists
        for dir in \${LD_LIBRARY_PATH//:/ } /usr/lib /usr/local/lib; do
            if [ -f "\$dir/libzstd_compress_file.so" ]; then
                ln -sf "\$dir/libzstd_compress_file.so" "\$RUNNING_DIR/libzstd_compress_file.so"
                break
            fi
        done
    fi
fi

# Execute the real DiffGenTool with all arguments
exec "\${SCRIPT_DIR}/diffgentool.bin" "\$@"
EOF
    chmod +x ${D}${bindir}/diffgentool

    bbnote "Installed DiffGenTool wrapper to ${D}${bindir}/diffgentool"
}

FILES:${PN} = "${bindir}/diffgentool ${bindir}/diffgentool.bin"

# Skip QA checks for .NET binary
INSANE_SKIP:${PN} = "already-stripped ldflags"
