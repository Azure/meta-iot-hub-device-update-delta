# Build and install the Microsoft GSL library

DESCRIPTION = "BSDiff"
AUTHOR = "zhuyie, Colin Percival"
HOMEPAGE = "https://github.com/zhuyie/bsdiff"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=f83b6905339a1462b8b70a6d742af235"

#
# OPTION#1 - Bind to specific branch and commit
#
SRC_URI = "git://github.com/zhuyie/bsdiff.git;protocol=https;branch=master \
           file://bsdiff-cmakelists.patch "

# Date: Feb 8, 2022 - This is based on iot-hub-device-update-delta/vcpkg/ports/bsdiff/portfile.cmake
#                     This requires bsdiff-cmakelistspath (see SRC_URI above)
SRCREV = "6b70bf123c725d181505c7bc181debf8236274e3"
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
#         d.setVar('S', d.getVar('WORKDIR') + "/bsdiff")
# }

DEPENDS = " bzip2"

inherit cmake

EXTRA_OECMAKE += " -DBUILD_STANDALONES=ON"
EXTRA_OECMAKE += " -DBUILD_SHARED_LIBS=OFF"

do_install:append() {
    install -d ${D}${includedir}
    install -m 0755 ${S}/include/bsdiff.h ${D}${includedir}/
    install -m 0744 ${S}/LICENSE ${D}${bindir}/bsdiff.LICENSE
    
    # Install static library and binaries
    install -d ${D}${libdir}
    install -m 0644 ${B}/libbsdiff.a ${D}${libdir}/
    install -m 0644 ${B}/3rdparty/libdivsufsort/lib/libdivsufsort.a ${D}${libdir}/
    install -m 0644 ${B}/3rdparty/libdivsufsort/lib/libdivsufsort64.a ${D}${libdir}/
    
    install -d ${D}${bindir}
    install -m 0755 ${B}/bsdiff ${D}${bindir}/
    install -m 0755 ${B}/bspatch ${D}${bindir}/
    
    # Create pkgconfig file for static linking
    install -d ${D}${libdir}/pkgconfig
    cat > ${D}${libdir}/pkgconfig/bsdiff.pc << EOF
prefix=${prefix}
exec_prefix=\${prefix}
libdir=\${exec_prefix}/lib
includedir=\${prefix}/include

Name: bsdiff
Description: Binary delta/diff utility library
Version: 1.0.0
Libs: -L\${libdir} -lbsdiff -ldivsufsort -ldivsufsort64 -lbz2
Cflags: -I\${includedir}
EOF
}

# Package files properly for static libraries
FILES:${PN} += "${bindir}/bsdiff ${bindir}/bspatch ${bindir}/bsdiff.LICENSE"
FILES:${PN}-dev += "${libdir}/libbsdiff.a ${includedir}/bsdiff.h ${libdir}/pkgconfig/bsdiff.pc"
FILES:${PN}-staticdev += "${libdir}/libdivsufsort.a ${libdir}/libdivsufsort64.a"

# Allow building as native package for use during builds
BBCLASSEXTEND = "native nativesdk"