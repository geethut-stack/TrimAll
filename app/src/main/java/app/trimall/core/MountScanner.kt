package app.trimall.core

data class MountEntry(
    val device: String,
    val mountPoint: String,
    val fsType: String,
    val options: String,
) {
    val readOnly get() = options.split(',').contains("ro")
}

object MountScanner {
    /**
     * Block-backed filesystems worth attempting. Read-only / unsupported ones (erofs, squashfs)
     * are still attempted so the history shows an explicit "unsupported" instead of silence.
     */
    private val fsTypes = setOf(
        "ext2", "ext3", "ext4", "f2fs", "xfs", "btrfs", "vfat", "exfat", "ntfs3", "erofs", "squashfs",
    )

    fun parse(procMounts: List<String>): List<MountEntry> {
        val all = procMounts.mapNotNull { line ->
            val p = line.trim().split(Regex("\\s+"))
            if (p.size < 4) null else MountEntry(unescape(p[0]), unescape(p[1]), p[2], p[3])
        }
        return all
            .filter { it.device.startsWith("/dev/") && it.fsType in fsTypes }
            // The same device is often bind-mounted several times; trimming it once is enough.
            .groupBy { it.device }
            .map { (_, list) -> list.minByOrNull { it.mountPoint.length }!! }
            .sortedBy { it.mountPoint }
    }

    private fun unescape(s: String) = s
        .replace("\\040", " ").replace("\\011", "\t").replace("\\012", "\n").replace("\\134", "\\")
}
