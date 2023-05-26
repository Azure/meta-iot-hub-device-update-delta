SUMMARY = "bitbake-layers recipe"
DESCRIPTION = "Recipe created by bitbake-layers"
LICENSE = "CLOSED"

ADU_DELTA_GIT_BRANCH ?= "main"

ADU_DELTA_SRC_URI ?= "gitsm://github.com/azure/iot-hub-device-update-delta"
SRC_URI = "${ADU_DELTA_SRC_URI};branch=${ADU_DELTA_GIT_BRANCH} \
          file://0001-ADU-v1.0.0-Yocto-mininum-build.patch \
          file://0002-Add-root-CMakeLists.txt.patch \
          file://0003-Fix-io_utility-CMakeLists.txt.patch \
          file://0004-Add-missing-header-in-zstd_decompression_reader.patch \
          "

ADU_DELTA_GIT_COMMIT ?= "b581e92458f458969b427051a2ac5d18d3528dc6"

SRCREV = "${ADU_DELTA_GIT_COMMIT}"

PV = "1.0+git${SRCPV}"
S = "${WORKDIR}/git"

DEPENDS = " ms-gsl bsdiff libgcrypt libgpg-error zlib zstd e2fsprogs"
RDEPENDS:${PN} += "bsdiff"

inherit cmake

# Build for Linux client.
EXTRA_OECMAKE += " -DUNIX=ON"

# Requires header file from bsdiff recipe.
do_compile[depends] += "bsdiff:do_prepare_recipe_sysroot"

# Suppress QA Issue: -dev package azure-device-update-diffs-dev contains non-symlink .so '/usr/lib/libadudiffapi.so' [dev-elf]
INSANE_SKIP:${PN} += " ldflags"
INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_SYSROOT_STRIP = "1"
SOLIBS = ".so"
FILES_SOLIBSDEV = ""

# Publish the library.
FILES:${PN} += "${libdir}/libadudiffapi.*"
