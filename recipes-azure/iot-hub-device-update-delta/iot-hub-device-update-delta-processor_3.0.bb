SUMMARY = "Azure IoT Hub Device Update Delta Processor"
DESCRIPTION = "C++ library for applying deltas and low-level delta creation"
HOMEPAGE = "https://github.com/Azure/iot-hub-device-update-delta"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=d4a904ca135bb7bc912156fee12726f0"

# Development mode: set to "1" to use local sources, "0" to use remote git
USE_LOCAL_SOURCES ?= "0"

# Conditional SRC_URI based on USE_LOCAL_SOURCES - use a known commit
# Note: Patch not included in SRC_URI - applied manually in do_patch:prepend()
SRC_URI = "git://github.com/Azure/iot-hub-device-update-delta.git;protocol=https;branch=user/nox-msft/scarthgap"

# Use the scarthgap branch commit (with fixes for algorithm rename and pkgconfig)
SRCREV = "${AUTOREV}"

S = "${WORKDIR}/git"
OECMAKE_SOURCEPATH = "${S}/src/native"

DEPENDS = "cmake-native ninja-native zlib zstd bzip2 libgcrypt fmt jsoncpp googletest bsdiff e2fsprogs libconfig openssl"

# Runtime dependencies
RDEPENDS:${PN} = "zlib zstd bzip2 libgcrypt fmt jsoncpp"

inherit cmake pkgconfig

# Set C++ standard
CXXFLAGS:append = " -std=c++17"

# CMake configuration
EXTRA_OECMAKE = " \
    -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_TESTING=OFF \
    -DCMAKE_PREFIX_PATH=${STAGING_DIR_TARGET}/usr \
"

# Set PKG_CONFIG_PATH for CMake to find pkgconfig files
export PKG_CONFIG_PATH = "${STAGING_LIBDIR}/pkgconfig"

# Set the source directory for CMake
OECMAKE_SOURCEPATH = "${S}/src/native"

# Install only the delta processor components
do_install:append() {
    # Install static libraries
    install -d ${D}${libdir}
    install -m 0644 ${B}/bin/libadudiffapi.a ${D}${libdir}/
    
    # Install headers directly to include directory (FindAzureIotHubDeviceUpdateDelta.cmake expects them here)
    install -d ${D}${includedir}
    cp -r ${S}/src/native/diffs/api/*.h ${D}${includedir}/
    
    # Install tools (excluding dumpextfs due to ext2fs API compatibility issues)
    install -d ${D}${bindir}
    # Install static library
    install -d ${D}${libdir}
    install -m 0644 ${B}/bin/libadudiffapi.a ${D}${libdir}/
    
    # Install all executables from build bin directory
    install -m 0755 ${B}/bin/applydiff ${D}${bindir}/
    install -m 0755 ${B}/bin/dumpdiff ${D}${bindir}/
    install -m 0755 ${B}/bin/extract ${D}${bindir}/
    install -m 0755 ${B}/bin/makecpio ${D}${bindir}/
    install -m 0755 ${B}/bin/recompress ${D}${bindir}/
    install -m 0755 ${B}/bin/zstd_compress_file ${D}${bindir}/
    
    # Install documentation
    install -d ${D}${prefix}/ms-adu_diffs
    install -m 0644 ${S}/LICENSE ${D}${prefix}/ms-adu_diffs/
    install -m 0644 ${S}/README.md ${D}${prefix}/ms-adu_diffs/
}

FILES:${PN} = "${bindir}/* ${prefix}/ms-adu_diffs/* ${libdir}/*.so*"
FILES:${PN}-dev = "${includedir}/*"
FILES:${PN}-staticdev = "${libdir}/*.a"

# Disable debug package splitting for simplicity
INHIBIT_PACKAGE_DEBUG_SPLIT = "1"

PACKAGES = "${PN} ${PN}-dev ${PN}-staticdev"

# Skip QA checks that might fail during development
INSANE_SKIP:${PN} = "dev-so"
