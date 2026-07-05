package com.example.watchhar.util

object ActivityLabels {
    private val labels27 = mapOf(
        0 to "Alarm clock",
        1 to "Blender in use",
        2 to "Brushing hair",
        3 to "Chopping",
        4 to "Clapping",
        5 to "Coughing",
        6 to "Drill in use",
        7 to "Drinking",
        8 to "Grating",
        9 to "Hair dryer in use",
        10 to "Hammering",
        11 to "Knocking",
        12 to "Laughing",
        13 to "Microwave",
        14 to "Pouring pitcher",
        15 to "Sanding",
        16 to "Scratching",
        17 to "Screwing",
        18 to "Shaver in use",
        19 to "Toilet flushing",
        20 to "Toothbrushing",
        21 to "Twisting jar",
        22 to "Vacuum in use",
        23 to "Washing utensils",
        24 to "Washing hands",
        25 to "Wiping with rag",
        26 to "Other"
    )

    private val labels19 = mapOf(
        0 to "Brushing hair",
        1 to "Chopping",
        2 to "Clapping",
        3 to "Drill in use",
        4 to "Drinking",
        5 to "Grating",
        6 to "Hammering",
        7 to "Knocking",
        8 to "Pouring pitcher",
        9 to "Sanding",
        10 to "Scratching",
        11 to "Screwing",
        12 to "Toothbrushing",
        13 to "Twisting jar",
        14 to "Vacuum in use",
        15 to "Washing utensils",
        16 to "Washing hands",
        17 to "Wiping with rag",
        18 to "Other"
    )

    private val labels20 = labels19 + mapOf(
        19 to "Unknown"
    )

    fun labelFor(classCount: Int, classIndex: Int): String {
        val labels = when (classCount) {
            19 -> labels19
            20 -> labels20
            27 -> labels27
            else -> emptyMap()
        }
        return labels[classIndex] ?: "Class $classIndex"
    }
}
