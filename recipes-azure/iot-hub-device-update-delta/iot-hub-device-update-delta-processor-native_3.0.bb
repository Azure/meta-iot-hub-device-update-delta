SUMMARY = "Azure IoT Hub Device Update Delta Processor (Native for build host)"
DESCRIPTION = "C++ library for creating/applying deltas - built for the build host (x86_64)"
HOMEPAGE = "https://github.com/Azure/iot-hub-device-update-delta"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=d4a904ca135bb7bc912156fee12726f0"

# Use same source as target recipe
require iot-hub-device-update-delta-processor_3.0.bb

# Inherit native to build for build host instead of target
inherit native

# Native dependencies (build host versions)
# Note: Not all dependencies have -native variants, only include what's needed for building
DEPENDS = "cmake-native ninja-native zlib-native zstd-native bzip2-native libgcrypt-native fmt-native jsoncpp-native bsdiff-native e2fsprogs-native libconfig-native openssl-native"

# Override CMake flags to disable testing (no googletest-native available)
EXTRA_OECMAKE += "-DBUILD_TESTING=OFF"

# No runtime dependencies for native recipes
RDEPENDS:${PN} = ""

# Install native library and tools
do_install:append() {
    # Library is already installed by base recipe
    # Make sure applydiff and other tools are available
    bbnote "Native libadudiffapi.so installed to ${D}${libdir}"
}
