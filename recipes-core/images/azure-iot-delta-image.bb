SUMMARY = "Azure IoT Hub Device Update Delta Image"
DESCRIPTION = "A custom image containing Azure IoT Device Update Delta components"

# Base this on core-image-minimal for now
inherit core-image

# Add our Azure IoT components
IMAGE_INSTALL:append = " \
    iot-hub-device-update-delta-processor \
    iot-hub-device-update-diff-generation \
    dotnet-core \
    lttng-ust \
    "

# Add some useful development tools
IMAGE_INSTALL:append = " \
    openssh \
    vim \
    htop \
    strace \
    gdb \
    "

# Set a reasonable image size
IMAGE_ROOTFS_SIZE ?= "2048"
IMAGE_ROOTFS_EXTRA_SPACE:append = "${@bb.utils.contains("DISTRO_FEATURES", "systemd", " + 4096", "", d)}"

# Include package management
IMAGE_FEATURES:append = " package-management"

# Set hostname
hostname_pn-base-files = "azure-iot-delta"

# Export image for easy deployment
IMAGE_FSTYPES += "tar.gz"