SUMMARY = "Azure IoT Hub Device Update Diff Generation"
DESCRIPTION = ".NET 6.0 application for creating deltas and high-level aspects"
HOMEPAGE = "https://github.com/Azure/iot-hub-device-update-delta"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=d4a904ca135bb7bc912156fee12726f0"

SRC_URI = "git://github.com/Azure/iot-hub-device-update-delta.git;protocol=https;branch=main"
SRCREV = "${AUTOREV}"

S = "${WORKDIR}/git"

# Set the dotnet project path
DOTNET_PROJECT = "src/managed/DiffGen/tools/DiffGenTool/DiffGenTool.csproj"

# Use the dotnet class for building
inherit dotnet

# Disable trimming to avoid JSON serialization issues
ENABLE_TRIMMING = "false"

# Override the default dependencies
DEPENDS += "iot-hub-device-update-delta-processor"
RDEPENDS:${PN} += "dotnet-core iot-hub-device-update-delta-processor"

# Override configure to handle our specific project structure
do_configure() {
    export HOME="${WORKDIR}"
    
    if [ -z ${DOTNET_PROJECT} ] ; then
     bberror "DOTNET_PROJECT must be specified!"
     exit -1
    fi
    cd ${S}
    
    # Backup and replace the NuGet.config with public sources only
    if [ -f "NuGet.config" ]; then
        mv NuGet.config NuGet.config.bak
        cat > NuGet.config << EOF
<?xml version="1.0" encoding="utf-8"?>
<configuration>
  <packageSources>
    <add key="nuget.org" value="https://api.nuget.org/v3/index.json" protocolVersion="3" />
  </packageSources>
</configuration>
EOF
    fi
    
    # Create the expected native library directory structure and copy our built libraries
    mkdir -p ${S}/src/native/x64-linux/Release/lib
    if [ -d "${STAGING_LIBDIR}" ]; then
        cp ${STAGING_LIBDIR}/libarchiveutility.so* ${S}/src/native/x64-linux/Release/lib/ 2>/dev/null || true
        cp ${STAGING_LIBDIR}/libdiffapi.so* ${S}/src/native/x64-linux/Release/lib/ 2>/dev/null || true
    fi
    
    # Fix the undefined SolutionDir variable in Directory.Build.props
    if [ -f "src/managed/DiffGen/Directory.Build.props" ]; then
        sed -i 's/\$(SolutionDir)/\$(SolutionRoot)/g' src/managed/DiffGen/Directory.Build.props
    fi
    
    dotnet restore ${DOTNET_PROJECT}
}

do_configure[network] = "1"

# Override install to handle our custom output structure
do_install() {
    install -d ${D}/${INSTALL_DIR}
    
    # The DiffGenTool is built to src/out/managed/Release/DiffGenTool/ instead of artifacts/publish
    if [ -d "${S}/src/out/managed/Release/DiffGenTool" ]; then
        cp -R --no-dereference --preserve=mode,links -v ${S}/src/out/managed/Release/DiffGenTool/* ${D}/${INSTALL_DIR}/
    else
        bberror "DiffGenTool output directory not found"
        exit 1
    fi
}

# Override install to create our custom wrapper
do_install:append() {
    # Create wrapper script
    install -d ${D}${bindir}
    cat > ${D}${bindir}/azure-delta-diffgen << 'SCRIPT_EOF'
#!/bin/bash
exec dotnet ${INSTALL_DIR}/DiffGenTool.dll "$@"
SCRIPT_EOF
    chmod +x ${D}${bindir}/azure-delta-diffgen
}

FILES:${PN} += "${bindir}/*"
