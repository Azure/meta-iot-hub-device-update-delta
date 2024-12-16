SUMMARY = "bitbake-layers recipe"
DESCRIPTION = "Recipe created by bitbake-layers"
LICENSE = "CLOSED"

ADU_DELTA_GIT_BRANCH ?= "main"

ADU_DELTA_SRC_URI ?= "git://github.com/Azure/iot-hub-device-update-delta.git"
SRC_URI = "${ADU_DELTA_SRC_URI};protocol=https;branch=${ADU_DELTA_GIT_BRANCH}"

ADU_DELTA_GIT_COMMIT ?= "d03a3bbbe3ad618d561e62519a515ee81a1e03b0"

SRCREV = "${ADU_DELTA_GIT_COMMIT}"

PV = "1.0+git${SRCPV}"
S = "${WORKDIR}/git"

DEPENDS = " ms-gsl bsdiff libgcrypt libgpg-error zlib zstd e2fsprogs"
RDEPENDS:${PN} += "bsdiff"

# Requires header file from bsdiff recipe.
do_compile[depends] += "bsdiff:do_prepare_recipe_sysroot"

do_compile() {

  ${S}/src/native/build.sh arm64-linux Release all

}

# Suppress QA Issue: -dev package azure-device-update-diffs-dev contains non-symlink .so '/usr/lib/libadudiffapi.so' [dev-elf]
INSANE_SKIP:${PN} += " ldflags"
INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_SYSROOT_STRIP = "1"
SOLIBS = ".so"
FILES_SOLIBSDEV = ""

# Publish the library.
FILES:${PN} += "${libdir}/libadudiffapi.*"