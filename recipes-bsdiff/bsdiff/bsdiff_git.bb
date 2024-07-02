# Build and install the Microsoft GSL library

DESCRIPTION = "BSDiff"
AUTHOR = "zhuyie, Colin Percival"
HOMEPAGE = "https://github.com/zhuyie/bsdiff"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=f83b6905339a1462b8b70a6d742af235"


# SRC_URI = "gitsm://github.com/microsoft/do-client;branch=main"
# SRCREV = "ef70c5c8a59d46820c648f434249516957966e7f"
# PV = "1.0+git${SRCPV}"
# S = "${WORKDIR}/git" 

#
# OPTION#1 - Bind to specific branch and commit
#
SRC_URI = "git://github.com/zhuyie/bsdiff.git;protocol=https;branch=master \
           file://bsdiff-cmakelists.patch "

# Date: Feb 8, 2022 - This is based on iot-hub-device-update-delta/vcpkg/ports/bsdiff/portfile.cmake
#                     This requires bsdiff-cmakelistspath (see SRC_URI above)
SRCREV = "d668332fa50fe55e74442dbee9e75acf26e40801"
S = "${WORKDIR}/git" 

#
# OPTION#2 - Following is an example for building bsdiff using a local tarball.
#
# BSDIFF_SRC_URI ?= "file://bsdiff.tar.gz"
# SRC_URI = "${BSDIFF_SRC_URI}"
# S = "${WORKDIR}/bsdiff" 

#
# OPTION#3 - Auto select based on BSDIFF_SRC_URI build variable.
#
# SRC_URI = "${BSDIFF_SRC_URI}"
#
# python () {
#     src_uri = d.getVar('SRC_URI')
#     if src_uri.startswith('git'):
#         d.setVar('SRCREV', d.getVar('AUTOREV'))
#         d.setVar('PV', '1.0+git' + d.getVar('SRCPV'))
#         d.setVar('S', d.getVar('WORKDIR') + "/git")
#     elif src_uri.startswith('file'):
#         d.setVar('S',  d.getVar('WORKDIR') + "/bsdiff")
# }

DEPENDS = " bzip2"

inherit cmake

EXTRA_OECMAKE += " -DBUILD_STANDALONES=ON"
EXTRA_OECMAKE += " -DBUILD_SHARED_LIBS=ON"

do_install:append() {
    install -d ${D}${includedir}
    install -m 0755 ${S}/include/bsdiff.h ${D}${includedir}/
    install -m 0744 ${S}/LICENSE ${D}${bindir}/bsdiff.LICENSE
}

# Suppress QA Issue: -dev package bsdiff-dev contains non-symlink .so '/usr/lib/libbsdiff.so' [dev-elf]
INSANE_SKIP:${PN} += " ldflags"
INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_SYSROOT_STRIP = "1"
SOLIBS = ".so"
FILES_SOLIBSDEV = ""

