# Only build shared library to avoid static linking issues
# When linking shared libraries, static libs without -fPIC cause relocation errors
EXTRA_OECMAKE += "-DBUILD_STATIC_LIBS=OFF"
