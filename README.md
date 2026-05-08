# meta-iot-hub-device-update-delta

Yocto meta-layer for building Azure IoT Hub Device Update Delta libraries and tools.

## Overview

This layer provides BitBake recipes to build the [iot-hub-device-update-delta](https://github.com/Azure/iot-hub-device-update-delta) project - a C++ library and toolset for creating and applying binary delta updates for IoT devices.

**What This Layer Provides:**
- `iot-hub-device-update-delta-processor` - Target library for delta operations
- `iot-hub-device-update-delta-processor-native` - Native tools (dumpextfs, applydiff)
- `iot-hub-device-update-delta-diffgentool-native` - Delta generation tool (diffgentool)
- `bsdiff` - Binary diff/patch utility (dependency)
- `azure-iot-delta-image` - Reference/test image recipe

**What This Layer Does NOT Provide:**
- Complete delta update workflow examples → See [meta-azure-device-update-samples](../meta-azure-device-update-samples/)
- SWUpdate integration recipes → See [meta-azure-device-update-samples](../meta-azure-device-update-samples/)
- Device update client → See [meta-azure-device-update](../meta-azure-device-update/)

## Table of Contents

- [Prerequisites](#prerequisites)
- [Layer Dependencies](#layer-dependencies)
- [Quick Start](#quick-start)
- [Recipes](#recipes)
- [Development Workflow](#development-workflow)
- [Configuration Variables](#configuration-variables)
- [Troubleshooting](#troubleshooting)

### Additional Documentation

- **[Delta Update Workflow Guide](docs/delta-update-workflow.md)** — End-to-end guide
  for generating and applying delta updates, including why recompression is required,
  tool inventory, best practices, and ADU agent integration.

## Prerequisites

**Yocto Version:** Scarthgap (5.0)

**Host Requirements:**
- Ubuntu 22.04 LTS (recommended) or compatible Linux distribution
- 8+ GB RAM (16 GB recommended for parallel builds)
- 50+ GB free disk space
- Swap space configured (8+ GB recommended)

**Required Packages:**
```bash
sudo apt-get update
sudo apt-get install -y gawk wget git diffstat unzip texinfo gcc build-essential \
  chrpath socat cpio python3 python3-pip python3-pexpect xz-utils debianutils \
  iputils-ping python3-git python3-jinja2 python3-subunit zstd liblz4-tool \
  file locales libacl1
```

## Layer Dependencies

This layer depends on:
- `openembedded-core` (poky) - Core Yocto layer (provides: libgcrypt, openssl, e2fsprogs, bzip2, zlib, zstd)
- `meta-openembedded/meta-oe` (openembedded-layer) - Extended recipes (provides: jsoncpp, libconfig, fmt, googletest)
- `meta-clang` (clang-layer) - LLVM/Clang toolchain

Layer configuration:
```bitbake
# conf/layer.conf
LAYERSERIES_COMPAT_iot-hub-device-update-delta = "scarthgap"
BBFILE_PRIORITY_iot-hub-device-update-delta = "6"
LAYERDEPENDS_iot-hub-device-update-delta = "core openembedded-layer networking-layer clang-layer"
```

### Recipe Overrides (.bbappend)

This layer modifies the following recipes from other layers:

| Recipe | Source Layer | Modification | Purpose |
|--------|-------------|--------------|---------|
| `e2fsprogs_1.47.0` | openembedded-core | Adds patch: `0002-add-ext2fs_file_get_current_physblock.patch` | Adds `ext2fs_file_get_current_physblock()` function required for ext4 delta processing |
| `jsoncpp_1.9.5` | meta-oe | Sets `EXTRA_OECMAKE += "-DBUILD_STATIC_LIBS=OFF"` | Disables static libs to avoid PIC relocation errors when linking shared libraries |
| `libconfig_1.7.3` | meta-oe | Sets `BBCLASSEXTEND = "native"` | Enables native build so libconfig can be used by processor-native recipes |

## Quick Start

### 1. Add Layer to Your Build

```bash
# Initialize Yocto build environment
source poky/oe-init-build-env build

# Add this layer
bitbake-layers add-layer /path/to/meta-iot-hub-device-update-delta

# Verify layer is added
bitbake-layers show-layers
```

### 2. Build Delta Processor Recipes

```bash
# Build target library
bitbake iot-hub-device-update-delta-processor

# Build native tools
bitbake iot-hub-device-update-delta-processor-native
bitbake iot-hub-device-update-delta-diffgentool-native
```

### 3. Verify Build Outputs

```bash
# Native tools location
ls -l tmp/sysroots-components/x86_64/iot-hub-device-update-delta-processor-native/usr/bin/
# Expected: dumpextfs, applydiff, adudiffapi

ls -l tmp/sysroots-components/x86_64/iot-hub-device-update-delta-diffgentool-native/usr/bin/
# Expected: diffgentool, diffgentool.bin

# Target library location
ls -l tmp/work/<MACHINE>/iot-hub-device-update-delta-processor/*/image/usr/lib/
# Expected: libadudiffapi.so*
```

## Recipes

### Core Recipes

#### iot-hub-device-update-delta-processor_3.0.bb
**Target library** for creating and applying binary deltas on IoT devices.

**Provides:**
- `libadudiffapi.so` - Core delta API library

**Dependencies:**
- `libconfig`
- `jsoncpp`
- `e2fsprogs` (for ext4 filesystem support)
- `bsdiff`

**Installation:**
```bitbake
IMAGE_INSTALL:append = " iot-hub-device-update-delta-processor"
```

#### iot-hub-device-update-delta-processor-native_3.0.bb
**Native tools** for build-time delta operations.

**Provides:**
- `dumpextfs` - Extract files from ext4 images
- `applydiff` - Apply binary deltas
- `adudiffapi` - Delta API command-line tool

**Usage:** Automatically available in build tasks via `DEPENDS`.

#### iot-hub-device-update-delta-diffgentool-native_3.0.bb
**Delta generation tool** for creating binary diffs between SWU files.

**Provides:**
- `diffgentool` - Shell wrapper
- `diffgentool.bin` - Main binary

**Usage:**
```bash
diffgentool.bin <source.swu> <target.swu> <output.diff> <working-dir>
```

#### bsdiff_1.0.0.bb
Binary diff/patch utility used by the delta processor.

**Provides:**
- `bsdiff` - Create binary patches
- `bspatch` - Apply binary patches

### Reference Recipe

#### azure-iot-delta-image.bb
Test/reference image recipe demonstrating delta processor integration.

**Usage:**
```bash
bitbake azure-iot-delta-image
```

## Development Workflow

### Local Source Development

For rapid iteration during development, use local source override:

#### Method 1: Environment Variable (Recommended)

```bash
# Set environment variable pointing to local git repo
export ADU_DELTA_LOCAL_SOURCE_DIR=~/projects/iot-hub-device-update-delta

# Initialize build environment
source poky/oe-init-build-env build

# Build with local sources
bitbake iot-hub-device-update-delta-processor-native
```

**How it works:**
- Recipe checks for `ADU_DELTA_LOCAL_SOURCE_DIR` environment variable
- If set, uses `file://${ADU_DELTA_LOCAL_SOURCE_DIR}` instead of git fetch
- Enables instant rebuilds after code changes (no git operations)

**Development loop:**
```bash
# 1. Edit C++ code in local repo
vim ~/projects/iot-hub-device-update-delta/src/delta_handler.cpp

# 2. Rebuild (typically 2-5 minutes)
bitbake -c compile -f iot-hub-device-update-delta-processor-native
bitbake -c install iot-hub-device-update-delta-processor-native

# 3. Test immediately
tmp/sysroots-components/x86_64/.../usr/bin/diffgentool --version
```

**Performance comparison:**
- Git fetch build: ~15-20 minutes (clean build)
- Local source build: ~2-5 minutes (incremental)
- Force compile only: ~30-60 seconds

#### Method 2: EXTERNALSRC (Alternative)

Add to `conf/local.conf`:
```bitbake
INHERIT += "externalsrc"
EXTERNALSRC:pn-iot-hub-device-update-delta-processor = "/path/to/iot-hub-device-update-delta"
EXTERNALSRC_BUILD:pn-iot-hub-device-update-delta-processor = "/path/to/iot-hub-device-update-delta/build"
```

### Clean Builds

```bash
# Clean specific recipe
bitbake -c cleanall iot-hub-device-update-delta-processor-native

# Clean shared state (faster rebuild)
bitbake -c cleansstate iot-hub-device-update-delta-processor-native

# Rebuild
bitbake iot-hub-device-update-delta-processor-native
```

### Recipe Debugging

```bash
# Show recipe variables
bitbake -e iot-hub-device-update-delta-processor | grep ^SRC_URI=

# Show build dependencies
bitbake -g iot-hub-device-update-delta-processor
cat pn-buildlist | grep delta

# Run specific task
bitbake -c configure iot-hub-device-update-delta-processor-native
bitbake -c compile iot-hub-device-update-delta-processor-native

# Check task logs
less tmp/work/*/iot-hub-device-update-delta-processor-native/*/temp/log.do_compile
```

## Configuration Variables

### ADU_DELTA_LOCAL_SOURCE_DIR

**Type:** Environment variable  
**Default:** Not set (uses git fetch)  
**Purpose:** Override source location for development builds

**Example:**
```bash
export ADU_DELTA_LOCAL_SOURCE_DIR=/home/user/iot-hub-device-update-delta
```

### WITH_FEATURE_DELTA_UPDATE

**Type:** BitBake variable  
**Default:** `"1"` (enabled)  
**Purpose:** Enable/disable delta update recipes

**Example in `conf/local.conf`:**
```bitbake
# Disable delta update recipes
WITH_FEATURE_DELTA_UPDATE = "0"
```

### Source Control Variables

These are set in the recipe and can be overridden in `local.conf`:

```bitbake
# Git repository
ADU_DELTA_SRC_URI = "git://github.com/Azure/iot-hub-device-update-delta.git"

# Git branch/tag
ADU_DELTA_SRCREV = "main"

# Alternative: Use specific tag
ADU_DELTA_SRCREV = "v3.0.0"
```

## Troubleshooting

### Build Fails: "No package 'jsoncpp' found"

**Cause:** Missing meta-oe layer dependency.

**Solution:**
```bash
bitbake-layers add-layer /path/to/meta-openembedded/meta-oe
```

### Build Fails: Memory Errors During Compilation

**Symptom:** `g++: fatal error: Killed signal terminated program cc1plus`

**Cause:** Insufficient RAM for parallel builds.

**Solution:**
```bash
# Reduce parallelization in conf/local.conf
BB_NUMBER_THREADS = "2"
PARALLEL_MAKE = "-j 2"

# Add swap space
sudo fallocate -l 8G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
```

### Local Source Changes Not Reflected

**Cause:** BitBake is using cached shared state.

**Solution:**
```bash
# Force recompile
bitbake -c compile -f iot-hub-device-update-delta-processor-native

# Or clean shared state
bitbake -c cleansstate iot-hub-device-update-delta-processor-native
bitbake iot-hub-device-update-delta-processor-native
```

### Native Tools Not Found in PATH

**Cause:** Native sysroot not in PATH during task execution.

**Solution:** Native tools are available via BitBake dependencies:

```bitbake
# In your recipe
DEPENDS += "iot-hub-device-update-delta-diffgentool-native"

do_generate_delta() {
    # Tools automatically in PATH
    diffgentool.bin --help
}
```

**Manual testing:**
```bash
# Add to PATH explicitly
export PATH="$(bitbake -e | grep ^STAGING_BINDIR_NATIVE= | cut -d'=' -f2 | tr -d '\"'):$PATH"
diffgentool --version
```

### CMake Configuration Errors

**Symptom:** `CMake Error: Could not find CMAKE_ROOT`

**Cause:** CMake cross-compilation environment issues.

**Solution:** Recipe already handles this via `cmake` bbclass. Check logs:
```bash
bitbake -c configure iot-hub-device-update-delta-processor 2>&1 | tee cmake-config.log
less tmp/work/*/iot-hub-device-update-delta-processor/*/temp/log.do_configure
```

### Recipe Version Conflicts

**Symptom:** Multiple versions of recipe found.

**Solution:**
```bash
# List available versions
bitbake-layers show-recipes iot-hub-device-update-delta-processor

# Prefer specific version in local.conf
PREFERRED_VERSION_iot-hub-device-update-delta-processor = "3.0"
```

## Layer Structure

```
meta-iot-hub-device-update-delta/
├── conf/
│   └── layer.conf                              # Layer configuration
├── recipes-azure/
│   └── iot-hub-device-update-delta/
│       ├── iot-hub-device-update-delta-processor_3.0.bb       # Target library
│       ├── iot-hub-device-update-delta-processor-native_3.0.bb # Native tools
│       ├── iot-hub-device-update-delta-diffgentool-native_3.0.bb # Diffgen tool
│       └── files/                              # Patches and config files
├── recipes-support/
│   └── bsdiff/
│       └── bsdiff_1.0.0.bb                    # Binary diff utility
├── recipes-devtools/
│   ├── e2fsprogs/                             # ext4 filesystem tools
│   ├── jsoncpp/                               # JSON library
│   └── libconfig/                             # Configuration file parser
├── recipes-core/
│   └── images/
│       └── azure-iot-delta-image.bb           # Reference test image
└── README.md                                  # This file
```

## Related Layers

- **[meta-azure-device-update-samples](../meta-azure-device-update-samples/)** - Complete delta update workflow examples with SWUpdate integration
- **[meta-azure-device-update](../meta-azure-device-update/)** - Azure Device Update client recipes
- **[meta-azure-device-update-bsp](../meta-azure-device-update-bsp/)** - Consolidated BSP layer with A/B update architecture for all boards

## License

See individual recipe files for licensing information. The layer itself follows the same license as the iot-hub-device-update-delta project.

## Contributing

This layer follows Yocto Project contribution guidelines. For issues with:
- **Recipes:** Report to this layer's issue tracker
- **Upstream code:** Report to [iot-hub-device-update-delta](https://github.com/Azure/iot-hub-device-update-delta/issues)

## References

- [iot-hub-device-update-delta Source](https://github.com/Azure/iot-hub-device-update-delta)
- [Yocto Project Documentation](https://docs.yoctoproject.org/)
- [Yocto Scarthgap Release Notes](https://docs.yoctoproject.org/5.0/)
- [Azure IoT Device Update Documentation](https://learn.microsoft.com/azure/iot-hub-device-update/)
