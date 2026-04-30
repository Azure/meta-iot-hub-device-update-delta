# Delta Update Workflow Guide

How to generate and apply binary delta updates using the ADU delta toolchain.

This guide is for consumers of the delta tools — whether integrating with
the ADU agent, building a custom OTA pipeline, or packaging updates for
distribution.

## Table of Contents

- [Tool Inventory](#tool-inventory)
- [End-to-End Workflow](#end-to-end-workflow)
- [Why Recompression Is Required](#why-recompression-is-required)
- [Step-by-Step: Generating a Delta](#step-by-step-generating-a-delta)
- [Step-by-Step: Applying a Delta (Reconstitution)](#step-by-step-applying-a-delta-reconstitution)
- [ADU Agent Integration](#adu-agent-integration)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)

## Tool Inventory

The delta toolchain consists of three layers. Which tools you need depends on
your use case.

### Generation Tools (build host / CI pipeline)

| Tool | Type | Purpose |
|------|------|---------|
| `recompress` | C++ native binary | Normalize SWU archive compression before diffing |
| `diffgentool` / `diffgentool.bin` | C# .NET 8.0 self-contained | Generate binary diff between two SWU files |
| `bsdiff` | C native binary | Low-level binary diff (called by diffgentool) |
| `zstd_compress_file` | C++ native binary | Zstd compression helper (called by diffgentool) |
| `dumpextfs` | C++ native binary | Ext4 filesystem analysis (called by diffgentool) |

### Apply Tools (build host, CI, or device-side testing)

| Tool | Type | Purpose |
|------|------|---------|
| `applydiff` | C++ native binary | Reconstruct target file from source + delta |
| `dumpdiff` | C++ native binary | Inspect delta file structure (debugging) |

### Device Library (target device)

| Component | Type | Purpose |
|-----------|------|---------|
| `libadudiffapi.so` | C++ shared library (aarch64) | Runtime delta application via the ADU agent |

The ADU agent calls `libadudiffapi.so` directly — it does **not** use
`applydiff` or `diffgentool`. The library performs the same reconstitution
logic that `applydiff` uses, but is invoked programmatically by the
delta update handler.

## End-to-End Workflow

```
                          BUILD HOST / CI
┌────────────────────────────────────────────────────────────┐
│                                                            │
│  source.swu ──► recompress ──► source-rc.swu ───┐         │
│                                                  │         │
│  target.swu ──► recompress ──► target-rc.swu ───┤         │
│                                                  │         │
│                                                  ▼         │
│                                            diffgentool     │
│                                                  │         │
│                                                  ▼         │
│                                             delta.diff     │
│                                                  │         │
└──────────────────────────────────────────────────┼─────────┘
                                                   │
                                        upload to ADU service
                                                   │
                                                   ▼
                          DEVICE (target)
┌────────────────────────────────────────────────────────────┐
│                                                            │
│  source-rc.swu ◄── already on device (current)            │
│       +                                                    │
│  delta.diff    ◄── downloaded from ADU service             │
│       │                                                    │
│       ▼                                                    │
│  libadudiffapi.so ──► reconstituted target-rc.swu         │
│                              │                             │
│                              ▼                             │
│                     SWUpdate installs to slot B            │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

> **Key insight:** The device must already have the **recompressed** source
> SWU as its current firmware. This means the very first deployment to a
> device must use a recompressed image. Subsequent delta updates then work
> because both sides share the same compression format.

## Why Recompression Is Required

Recompression is a **mandatory normalization step** before generating deltas.
Skipping it will produce deltas that are nearly as large as the full image.

### The Problem

An SWU file is a cpio archive containing compressed image payloads (e.g.,
`rootfs.ext4.gz`). Compression algorithms like gzip are **chaotic** — a
single byte change in the uncompressed input can cascade into completely
different compressed output. This means:

- `bsdiff(source.ext4.gz, target.ext4.gz)` → **large** delta (nearly full size)
- `bsdiff(source.ext4.zst, target.ext4.zst)` → **small** delta (only actual changes)

The second case works because both files were compressed by the **same tool
with identical settings**, producing structurally similar compressed streams.

### What `recompress` Does

The `recompress` tool:

1. Opens the SWU (cpio archive)
2. For each image payload marked as compressed (gzip/zlib) in `sw-description`:
   - Decompresses it (gzip/zlib decompression)
   - Recompresses it using **gzip at Z_BEST_COMPRESSION with gz init type**
3. Updates the `sw-description` file inside the SWU:
   - Recalculates SHA-256 hashes for the recompressed payloads
4. Optionally re-signs the `sw-description` (if a signing command is provided);
   if no signing command is given, any existing `sw-description.sig` is removed
5. Writes a new SWU with the normalized payloads

### Why Gzip?

The C++ `recompress` binary normalizes to **gzip** (not zstd). This is the
tested and verified path used by the Yocto recipes. The fixed settings
(Z_BEST_COMPRESSION, gz init type) produce consistent output that DiffGenTool
can analyze and reproduce via its `ZlibCompression` recipe type.

> **Note:** The Python-based `recompress_tool.py` in the upstream
> `iot-hub-device-update-delta` repo normalizes to **zstd** instead. These
> are **not interchangeable** — you must use the same tool for both source
> and target. The Yocto recipes use the C++ `recompress` binary.

### What About DiffGenTool's Built-in Compression Awareness?

DiffGenTool **is** compression-aware — it can detect gzip compression levels
(`diff_get_zlib_compression_level`) and emit `ZlibCompression` /
`ZstdDecompression` recipes in the diff. However, this operates at the
**file level within** the SWU, not at the **SWU archive level**. The
recompress step ensures the SWU-level cpio structure and payload compression
are consistent so DiffGenTool receives clean, comparable inputs.

## Step-by-Step: Generating a Delta

### Prerequisites

- Two SWU update images (source and target versions)
- Delta tools installed (see [main README](../README.md) for build instructions)
- Signing key pair (for production; optional for testing)

### 1. Recompress Both SWU Files

```bash
# Recompress without signing (testing, or sign separately)
recompress swu source.swu source-recompressed.swu
recompress swu target.swu target-recompressed.swu

# Sign separately (this is the flow used by the Yocto sample recipes):
# recompress produces an unsigned SWU, then you sign with your own tooling
openssl cms -sign -signer cert.pem -inkey key.pem -binary -noattr \
    -in source-recompressed-dir/sw-description \
    -out source-recompressed-dir/sw-description.sig

# Alternatively, recompress can sign inline (upstream capability):
# recompress swu source.swu source-recompressed.swu "openssl cms -sign ..."
```

> **Note:** When no signing command is provided, `recompress` removes any
> existing `sw-description.sig` from the output SWU. The Yocto sample
> recipes (`adu-delta-image.bb`) recompress first, then sign in a separate
> step.

### 2. Generate the Delta

```bash
diffgentool source-recompressed.swu target-recompressed.swu delta.diff /path/to/working-dir

# With explicit log folder:
diffgentool source-recompressed.swu target-recompressed.swu delta.diff /path/to/log-dir /path/to/working-dir
```

DiffGenTool will:
- Tokenize both SWU archives (parse cpio, detect compression, analyze ext4)
- Compute optimal diff recipes (bsdiff, zstd, slice, chain)
- Write `delta.diff` containing the recipes and delta data

### 3. Verify the Delta (optional but recommended)

```bash
# Reconstruct the target from source + delta
applydiff source-recompressed.swu delta.diff target-reconstructed.swu

# Compare hashes
sha256sum target-recompressed.swu target-reconstructed.swu
# These MUST match
```

### 4. Inspect the Delta (debugging)

```bash
dumpdiff delta.diff
dumpdiff delta.diff --skip_recipes
```

## Step-by-Step: Applying a Delta (Reconstitution)

### Standalone (testing / CI)

```bash
applydiff source-recompressed.swu delta.diff target-reconstructed.swu
```

### On Device (via ADU Agent)

The ADU agent handles reconstitution automatically through its delta update
handler, which calls `libadudiffapi.so`. The flow is:

1. ADU agent downloads `delta.diff` from the Device Update service
2. Delta handler retrieves the source SWU from the ADU delta cache
   (`/var/lib/adu/sdc/`) — this is the base artifact from the previously
   installed update
3. `libadudiffapi.so` applies the delta recipes to reconstruct the target SWU
4. The reconstructed SWU is passed to SWUpdate for installation

No manual intervention is needed on the device side.

## ADU Agent Integration

### Import Manifest Requirements

To deploy a delta update through the ADU service, you need:

1. **Recompressed target SWU** — the full update file (uploaded as a reference)
2. **delta.diff** — the binary delta file (uploaded as the payload)
3. **Import manifest** with:
   - `downloadHandler.id` set to `"microsoft/delta:1"`
   - The **source file hash** (SHA-256 of the recompressed source SWU)
   - Standard update metadata (manufacturer, model, version)

For import manifest examples, see
[README-DELTA-UPDATES.md](../meta-azure-device-update-samples/docs/README-DELTA-UPDATES.md)
in `meta-azure-device-update-samples`.

### Device-Side Components

For the ADU agent to use delta updates, it must be built with delta handler
support. See
[building-with-delta-handler.md](https://github.com/Azure/iot-hub-device-update/blob/feature/vnext-delta/docs/agent-reference/building-with-delta-handler.md)
in the `iot-hub-device-update` repository.

The key components on the device:

| Component | Yocto Recipe | Install Location |
|-----------|-------------|-----------------|
| `libadudiffapi.so` | `iot-hub-device-update-delta-processor` | `/usr/lib/` |
| Delta handler | Built into ADU agent | Part of `azure-device-update` recipe |

## Best Practices

### Compression Consistency

- **Always recompress both source and target** with the same `recompress`
  binary before generating a delta.
- Never mix the Python `recompress_tool.py` with the C++ `recompress` binary
  — they use different compression formats (zstd vs gzip).
- The base image deployed to devices must be the **recompressed** version.
  A delta generated from recompressed images cannot be applied against the
  original (non-recompressed) image.

### Signing

- Always sign recompressed SWU files for production deployments.
- Use the same signing mechanism for all images in a deployment chain.
- The `recompress` tool accepts an optional signing command as its last
  argument — use this rather than signing separately to ensure the
  `sw-description.sig` is correctly embedded in the cpio archive.

### Delta Size Expectations

| Scenario | Typical Delta Size |
|----------|--------------------|
| Minor config change | 1–5% of full image |
| Package update (few packages) | 5–15% of full image |
| Major OS upgrade | 30–60% of full image |
| Completely different image | ~100% (no benefit) |

If your delta is unexpectedly large, verify that both inputs were
recompressed with the same tool and settings.

### CI Pipeline Integration

For Yocto-integrated CI pipeline examples (multi-version builds, automated
recompression, and delta generation), see the `adu-delta-image` recipe in
[meta-azure-device-update-samples](../meta-azure-device-update-samples/).

## Troubleshooting

### Delta is nearly as large as the full image

- **Cause:** Source and/or target SWU was not recompressed, or they were
  recompressed with different tools/settings.
- **Fix:** Recompress both with the same `recompress` binary.

### `applydiff` produces a file that doesn't match the target

- **Cause:** The source file used for reconstitution doesn't match the source
  used during delta generation.
- **Fix:** Verify SHA-256 hashes of the source file on both sides.

### `recompress` fails with "Failed to parse sw-description"

- **Cause:** The SWU's `sw-description` uses a format that libconfig cannot
  parse, or the file is corrupted.
- **Fix:** Extract the SWU manually (`cpio -idmv < file.swu`) and inspect
  `sw-description`.

### `diffgentool` runs out of memory

- **Cause:** DiffGenTool loads archive contents into memory during
  tokenization. Large images (>2GB) may exceed available RAM.
- **Fix:** Ensure at least 8GB of RAM available. For very large images,
  use a machine with 16GB+.

### Device fails to apply delta — hash mismatch

- **Cause:** The base image on the device is not the recompressed version.
- **Fix:** Re-flash the device with the recompressed base image, then retry
  the delta update.
