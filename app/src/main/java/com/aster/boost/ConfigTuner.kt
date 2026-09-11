package com.aster.boost

object ConfigTuner {
    private val mtuRegex = Regex("(?im)^\\s*MTU\\s*=\\s*(\\d+)\\s*$")

    fun extractMtu(config: String): Int? =
        mtuRegex.find(config)?.groupValues?.getOrNull(1)?.toIntOrNull()

    fun candidateMtus(config: String): List<Int> =
        listOfNotNull(extractMtu(config), 1420, 1380, 1360, 1320)
            .filter { it in 1200..1500 }
            .distinct()

    fun withMtu(config: String, mtu: Int): String {
        require(mtu in 1200..1500)

        val lines = config.lines().toMutableList()
        val interfaceStart = lines.indexOfFirst { it.trim().equals("[Interface]", ignoreCase = true) }
        if (interfaceStart < 0) return config

        var nextSection = lines.size
        for (i in interfaceStart + 1 until lines.size) {
            val t = lines[i].trim()
            if (t.startsWith("[") && t.endsWith("]")) {
                nextSection = i
                break
            }
        }

        for (i in interfaceStart + 1 until nextSection) {
            if (lines[i].trim().startsWith("MTU", ignoreCase = true)) {
                lines[i] = "MTU = $mtu"
                return lines.joinToString("\n")
            }
        }

        lines.add(interfaceStart + 1, "MTU = $mtu")
        return lines.joinToString("\n")
    }
}
