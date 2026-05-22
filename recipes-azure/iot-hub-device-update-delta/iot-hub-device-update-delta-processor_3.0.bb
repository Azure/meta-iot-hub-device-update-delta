SUMMARY = "Azure IoT Hub Device Update Delta Processor"
DESCRIPTION = "C++ library for applying deltas and low-level delta creation"
HOMEPAGE = "https://github.com/Azure/iot-hub-device-update-delta"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=d4a904ca135bb7bc912156fee12726f0"

# Development mode: set to "1" to use local sources, "0" to use remote git
USE_LOCAL_SOURCES ?= "0"

# Build testing: set to "1" to enable unit tests, "0" to disable (default)
BUILD_TESTING ?= "0"

# Conditional SRC_URI based on USE_LOCAL_SOURCES - use a known commit
# Note: Patch not included in SRC_URI - applied manually in do_patch:prepend()
SRC_URI = "${ADU_DELTA_SRC_URI};protocol=https;branch=${ADU_DELTA_GIT_BRANCH}"

# Use the scarthgap branch commit (with fixes for algorithm rename and pkgconfig)
SRCREV = "${AUTOREV}"

S = "${WORKDIR}/git"
OECMAKE_SOURCEPATH = "${S}/src/native"

# Base dependencies
DEPENDS = "cmake-native ninja-native zlib zstd bzip2 libgcrypt fmt jsoncpp bsdiff e2fsprogs libconfig openssl"

# Add googletest when testing is enabled
DEPENDS += "${@'googletest' if d.getVar('BUILD_TESTING') == '1' else ''}"

# Runtime dependencies
RDEPENDS:${PN} = "zlib zstd bzip2 libgcrypt fmt jsoncpp"

inherit cmake pkgconfig

# Set C++ standard
CXXFLAGS:append = " -std=c++17"

# CMake configuration - BUILD_TESTING controlled by recipe variable
EXTRA_OECMAKE = " \
    -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_TESTING=${@'ON' if d.getVar('BUILD_TESTING') == '1' else 'OFF'} \
    -DBUILD_SHARED_LIBS=ON \
    -DCMAKE_PREFIX_PATH=${STAGING_DIR_TARGET}/usr \
    -DCMAKE_RUNTIME_OUTPUT_DIRECTORY=${B}/bin \
    -DCMAKE_LIBRARY_OUTPUT_DIRECTORY=${B}/lib \
"

# Set PKG_CONFIG_PATH for CMake to find pkgconfig files
export PKG_CONFIG_PATH = "${STAGING_LIBDIR}/pkgconfig"

# Set the source directory for CMake
OECMAKE_SOURCEPATH = "${S}/src/native"

# Skip gtest subdirectories only when not building tests
do_configure:prepend() {
    if [ "${BUILD_TESTING}" != "1" ]; then
        # Comment out all gtest subdirectories in CMakeLists.txt
        sed -i 's|^add_subdirectory(.*gtest)|#&|g' ${S}/src/native/CMakeLists.txt
    fi
}

# Install only the delta processor components
do_install:append() {
    # Install libraries (both shared and static)
    install -d ${D}${libdir}
    
    # Install shared library
    if [ -f ${B}/lib/libadudiffapi.so ]; then
        install -m 0755 ${B}/lib/libadudiffapi.so ${D}${libdir}/
    fi
    
    # Install static library
    if [ -f ${B}/lib/libadudiffapi.a ]; then
        install -m 0644 ${B}/lib/libadudiffapi.a ${D}${libdir}/
    fi
    
    # Install headers directly to include directory (FindAzureIotHubDeviceUpdateDelta.cmake expects them here)
    install -d ${D}${includedir}
    cp -r ${S}/src/native/diffs/api/*.h ${D}${includedir}/
    
    # Install tools
    install -d ${D}${bindir}
    
    # Install all executables from build bin directory
    install -m 0755 ${B}/bin/applydiff ${D}${bindir}/
    install -m 0755 ${B}/bin/dumpdiff ${D}${bindir}/
    install -m 0755 ${B}/bin/dumpextfs ${D}${bindir}/
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
