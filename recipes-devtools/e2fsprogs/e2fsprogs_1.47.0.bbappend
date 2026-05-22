# Append to e2fsprogs recipe to add Azure-specific patches
#
# This layer adds the ext2fs_file_get_current_physblock function
# which is required for delta processing of ext4 filesystems

FILESEXTRAPATHS:prepend := "${THISDIR}/e2fsprogs:"

SRC_URI:append = " file://0002-add-ext2fs_file_get_current_physblock.patch"

