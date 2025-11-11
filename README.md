# Meta IoT Hub Device Update Delta Layer

This meta-layer provides recipes to build the Azure IoT Hub Device Update Delta project using the Yocto Project framework with Scarthgap release.

## Overview

The Azure IoT Hub Device Update Delta project enables efficient delta updates for IoT devices by creating and applying binary diffs. This meta-layer builds the core delta processing libraries with minimal dependencies focused on embedded Linux systems.

## Layer Dependencies

This layer depends on the following layers:

- **meta** (from poky)
- **meta-poky** (from poky)  
- **meta-yocto-bsp** (from poky)
- **meta-oe** (from meta-openembedded)
- **meta-networking** (from meta-openembedded)
- **meta-python** (from meta-openembedded)
- **meta-clang** (from meta-clang)

### Required External Dependencies

The following system dependencies must be available in your Yocto build:

1. **Core Libraries:**
   - `zlib` - Compression library
   - `libzstd` - Zstandard compression
   - `bzip2` - Bzip2 compression
   - `libgcrypt` - Cryptographic library
   - `openssl` - SSL/TLS library

2. **C++ Libraries:**
   - `fmt` - Modern formatting library
   - `jsoncpp` - JSON processing library
   - `googletest` - Unit testing framework (for tests)

3. **Build Tools:**
   - `cmake` >= 3.16
   - `ninja-build`
   - `pkgconfig`
   - C++20 capable compiler (GCC/Clang)

## Package Manager Configuration

### Recommended: OPKG (IPK packages)

For optimal .NET runtime compatibility and embedded systems support, configure your build to use OPKG package manager:

```bash
# In conf/local.conf
PACKAGE_CLASSES ?= "package_ipk"
```

**Benefits of OPKG:**
- Better .NET dependency resolution (handles `lttng-ust` requirements)
- Designed for embedded systems (smaller footprint)
- More efficient dependency handling vs RPM/DNF
- Used by OpenWrt and other embedded distributions

### Alternative: RPM packages (Default)

If you prefer RPM packages (default Poky behavior):

```bash
# In conf/local.conf  
PACKAGE_CLASSES ?= "package_rpm"
```

**Note:** RPM/DNF may require additional configuration for .NET runtime dependencies.

### .NET Runtime Dependencies

The .NET components require LTTng (Linux Trace Toolkit) for runtime tracing:

- **`lttng-ust`** - User Space Tracing library for .NET runtime
- **`lttng-tools`** - LTTng command-line tools (optional for debugging)

These are automatically included in recipes but may need manual installation if building individual components.

## Recipes Provided

### iot-hub-device-update-delta-processor

**File:** `recipes-azure/iot-hub-device-update-delta/iot-hub-device-update-delta-processor_1.0.bb`

**Description:** Main recipe that builds the Azure IoT Hub Device Update Delta processor libraries.

**Key Features:**
- Builds core delta processing libraries (`libdiffs.so`, `libdiff_api.so`)
- Includes comprehensive dependency management via PkgConfig
- Automated CMake configuration patching
- Tests enabled by default using ptest framework
- Tools subdirectories disabled to minimize dependencies

**Output:**
- Libraries: `/usr/lib/libdiffs.so`, `/usr/lib/libdiff_api.so`
- Headers: `/usr/include/azure-delta/`
- Tools: `/usr/bin/applydiff` (if tools are enabled)

### bsdiff

**File:** `recipes-support/bsdiff/bsdiff_1.0.bb`

**Description:** Custom bsdiff recipe providing binary delta functionality required by the main processor.

**Key Features:**
- CMake-based build system
- Shared library with proper -fPIC flags
- Custom pkgconfig file generation
- Integration with main recipe via PkgConfig detection

## Build Instructions

### 1. Environment Setup

```bash
# Clone required repositories
git clone git://git.yoctoproject.org/poky -b scarthgap
git clone git://git.openembedded.org/meta-openembedded -b scarthgap  
git clone https://github.com/kraj/meta-clang -b scarthgap

# Initialize build environment
source poky/oe-init-build-env build-adu-delta
```

### 2. Configure bblayers.conf

Add the following layers to your `conf/bblayers.conf`:

```bitbake
BBLAYERS ?= " \
  /path/to/poky/meta \
  /path/to/poky/meta-poky \
  /path/to/poky/meta-yocto-bsp \
  /path/to/meta-openembedded/meta-oe \
  /path/to/meta-openembedded/meta-networking \
  /path/to/meta-openembedded/meta-python \
  /path/to/meta-clang \
  /path/to/meta-iot-hub-device-update-delta \
  "
```

### 3. Configure local.conf

Add these settings to your `conf/local.conf`:

```bitbake
# Enable tests
DISTRO_FEATURES:append = " ptest"

# Ensure C++20 support
TOOLCHAIN = "clang"
```

### 4. Build the Recipe

```bash
# Build the main recipe
bitbake iot-hub-device-update-delta-processor

# Or build for specific steps
bitbake -c configure iot-hub-device-update-delta-processor
bitbake -c compile iot-hub-device-update-delta-processor
```

## Technical Implementation Details

### Dependency Resolution Strategy

The recipe uses PkgConfig for dependency detection instead of CMake's find_package to ensure compatibility with Yocto's cross-compilation environment:

1. **Automated CMake Patching:** Uses sed scripts to replace `find_package()` calls with `pkg_check_modules()`
2. **Custom PkgConfig Files:** Creates missing .pc files for dependencies like bsdiff
3. **Include Directory Management:** Automatically adds include directories for dependencies

### Applied Patches and Fixes

The configure step applies the following automated patches:

#### 1. Zstd Detection Fix
```cmake
# Replaces:
find_package(zstd REQUIRED)
target_link_libraries(target zstd::libzstd)

# With:
find_package(PkgConfig REQUIRED)
pkg_check_modules(ZSTD REQUIRED libzstd)
target_link_libraries(target ${ZSTD_LIBRARIES})
```

#### 2. Bsdiff Integration
```cmake
# Replaces:
find_package(bsdiff REQUIRED)
target_link_libraries(target bsdiff::bsdiff)

# With:
find_package(PkgConfig REQUIRED)
pkg_check_modules(BSDIFF REQUIRED bsdiff)
target_link_libraries(target ${BSDIFF_LIBRARIES})
```

#### 3. Namespace Collision Fix
Addresses C++ namespace conflicts between the project's `algorithm` enum and STL `<algorithm>` header:

```cpp
// Changes enum definition from:
enum class algorithm : uint32_t

// To:
enum class hash_algorithm : uint32_t
```

### Known Issues and Workarounds

#### 1. C++ Namespace Conflicts
**Issue:** The source code defines an enum named `algorithm` which conflicts with STL headers.
**Status:** Partially resolved through automated sed replacements.
**Workaround:** Tools subdirectories are disabled to avoid complex namespace conflicts in tool code.

#### 2. Complex Tool Dependencies
**Issue:** The tools subdirectory requires additional dependencies (e2fsprogs, libconfig).
**Workaround:** Tools are disabled by default. Core libraries build successfully without them.

## Testing

### Enabling Tests

Tests are enabled by default in the recipe. The recipe inherits from `ptest` class and automatically discovers and runs unit tests.

```bash
# Run tests during build
bitbake iot-hub-device-update-delta-processor

# Run tests on target device
ptest-runner iot-hub-device-update-delta-processor
```

### Test Framework

- Uses GoogleTest framework
- Tests are automatically discovered and configured
- Test binaries are installed to `/usr/lib/iot-hub-device-update-delta-processor/ptest/`

## Customization Options

### Enabling Tools (Advanced)

To enable the tools subdirectory (requires additional dependencies):

1. Remove the tools disabling line from the recipe:
```bitbake
# Comment out this line in do_configure:append():
# sed -i '/add_subdirectory(tools\//s/^/#/' "${S}/src/native/CMakeLists.txt"
```

2. Add additional dependencies:
```bitbake
DEPENDS += "e2fsprogs libconfig"
```

### Custom Patches

To apply additional source patches:

1. Create patch files in `files/` directory
2. Add to SRC_URI:
```bitbake
SRC_URI += "file://custom-fix.patch"
```

## Troubleshooting

### Common Build Issues

#### 1. Missing Dependencies
**Error:** `Package 'libzstd' not found`
**Solution:** Ensure meta-oe layer is properly added to bblayers.conf

#### 2. CMake Configuration Failures
**Error:** `Could NOT find zstd`
**Solution:** The automated patching should resolve this. Check that do_configure:append() is executing properly.

#### 3. C++ Compilation Errors
**Error:** Namespace conflicts with `algorithm`
**Solution:** The current recipe includes namespace fixes. For complete resolution, additional source patches may be needed.

#### 4. .NET Runtime LTTng Dependency Error
**Error:** `nothing provides liblttng-ust.so.0()(64bit) needed by iot-hub-device-update-diff-generation`
**Root Cause:** .NET runtime requires LTTng (Linux Trace Toolkit) for EventSource tracing
**Solutions:**
1. **Use OPKG package manager (Recommended):**
   ```bash
   # In conf/local.conf
   PACKAGE_CLASSES ?= "package_ipk"
   ```
2. **Add LTTng to your image:**
   ```bash
   IMAGE_INSTALL:append = " lttng-ust lttng-tools"
   ```
3. **Verify LTTng availability:**
   ```bash
   bitbake-layers show-recipes "*lttng*"
   ```

### Debug Information

To debug build issues:

```bash
# Enable verbose builds
bitbake -c compile iot-hub-device-update-delta-processor -v

# Check configure logs
bitbake -c configure iot-hub-device-update-delta-processor
# Check: tmp/work/.../log.do_configure.*

# Examine patched sources
# Check: tmp/work/.../git/src/native/
```

## Performance Considerations

### Build Optimization

- **Parallel Builds:** The recipe supports parallel compilation with ninja
- **Cross-compilation:** Optimized for Yocto's cross-compilation environment
- **Minimal Dependencies:** Tools are disabled to reduce build complexity

### Runtime Performance

- **Library Size:** Core libraries are optimized for embedded systems
- **Memory Usage:** Efficient delta processing algorithms
- **CPU Usage:** C++20 optimizations enabled

## Version Information

- **Recipe Version:** 1.0
- **Source Version:** HEAD from Azure IoT Hub Device Update Delta repository
- **Yocto Compatibility:** Scarthgap (5.0.x)
- **License:** MIT

## Contributing

When contributing to this meta-layer:

1. Follow Yocto Project coding standards
2. Test changes with clean builds
3. Update this README for significant changes
4. Ensure compatibility with Scarthgap release

## References

- [Azure IoT Hub Device Update Delta](https://github.com/azure/iot-hub-device-update-delta)
- [Yocto Project Documentation](https://docs.yoctoproject.org/)
- [Meta-OpenEmbedded](http://git.openembedded.org/meta-openembedded/)
- [Meta-Clang](https://github.com/kraj/meta-clang)

## Support

For issues specific to this meta-layer, check:

1. Build logs in `tmp/work/.../temp/log.*`
2. CMake configuration in `tmp/work/.../build/`
3. Source patches applied in `tmp/work/.../git/`

For Azure IoT Hub Device Update Delta issues, refer to the upstream project repository.