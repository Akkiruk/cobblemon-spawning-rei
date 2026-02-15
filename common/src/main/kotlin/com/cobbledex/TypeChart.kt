package com.cobbledex

object TypeChart {

    val TYPES = listOf(
        "normal", "fire", "water", "electric", "grass", "ice",
        "fighting", "poison", "ground", "flying", "psychic", "bug",
        "rock", "ghost", "dragon", "dark", "steel", "fairy"
    )

    // chart[attacking][defending] = multiplier
    private val chart: Map<String, Map<String, Float>> by lazy { buildChart() }

    fun effectiveness(attacking: String, defending: String): Float =
        chart[attacking.lowercase()]?.get(defending.lowercase()) ?: 1f

    fun defensiveMatchups(primary: String, secondary: String?): Map<String, Float> {
        val result = mutableMapOf<String, Float>()
        for (atk in TYPES) {
            val m1 = effectiveness(atk, primary)
            val m2 = if (secondary != null && secondary != primary) effectiveness(atk, secondary) else 1f
            val total = m1 * m2
            if (total != 1f) result[atk] = total
        }
        return result
    }

    data class TypeMatchups(
        val weaknesses: Map<String, Float>,   // 2x or 4x
        val resistances: Map<String, Float>,  // 0.5x or 0.25x
        val immunities: List<String>          // 0x
    )

    fun getMatchups(primary: String, secondary: String?): TypeMatchups {
        val all = defensiveMatchups(primary, secondary)
        return TypeMatchups(
            weaknesses = all.filter { it.value > 1f }
                .entries.sortedWith(compareByDescending<Map.Entry<String, Float>> { it.value }.thenBy { it.key })
                .associate { it.key to it.value },
            resistances = all.filter { it.value in 0.01f..0.99f }
                .entries.sortedWith(compareBy<Map.Entry<String, Float>> { it.value }.thenBy { it.key })
                .associate { it.key to it.value },
            immunities = all.filter { it.value == 0f }.keys.sorted()
        )
    }

    @Suppress("LongMethod")
    private fun buildChart(): Map<String, Map<String, Float>> {
        val c = mutableMapOf<String, MutableMap<String, Float>>()
        for (t in TYPES) c[t] = mutableMapOf()

        fun se(atk: String, def: String, m: Float) { c[atk]!![def] = m }

        // Normal attacking
        se("normal", "rock", 0.5f); se("normal", "ghost", 0f); se("normal", "steel", 0.5f)

        // Fire attacking
        se("fire", "fire", 0.5f); se("fire", "water", 0.5f); se("fire", "grass", 2f)
        se("fire", "ice", 2f); se("fire", "bug", 2f); se("fire", "rock", 0.5f)
        se("fire", "dragon", 0.5f); se("fire", "steel", 2f)

        // Water attacking
        se("water", "fire", 2f); se("water", "water", 0.5f); se("water", "grass", 0.5f)
        se("water", "ground", 2f); se("water", "rock", 2f); se("water", "dragon", 0.5f)

        // Electric attacking
        se("electric", "water", 2f); se("electric", "electric", 0.5f); se("electric", "grass", 0.5f)
        se("electric", "ground", 0f); se("electric", "flying", 2f); se("electric", "dragon", 0.5f)

        // Grass attacking
        se("grass", "fire", 0.5f); se("grass", "water", 2f); se("grass", "grass", 0.5f)
        se("grass", "poison", 0.5f); se("grass", "ground", 2f); se("grass", "flying", 0.5f)
        se("grass", "bug", 0.5f); se("grass", "rock", 2f); se("grass", "dragon", 0.5f)
        se("grass", "steel", 0.5f)

        // Ice attacking
        se("ice", "fire", 0.5f); se("ice", "water", 0.5f); se("ice", "grass", 2f)
        se("ice", "ice", 0.5f); se("ice", "ground", 2f); se("ice", "flying", 2f)
        se("ice", "dragon", 2f); se("ice", "steel", 0.5f)

        // Fighting attacking
        se("fighting", "normal", 2f); se("fighting", "ice", 2f); se("fighting", "poison", 0.5f)
        se("fighting", "flying", 0.5f); se("fighting", "psychic", 0.5f); se("fighting", "bug", 0.5f)
        se("fighting", "rock", 2f); se("fighting", "ghost", 0f); se("fighting", "dark", 2f)
        se("fighting", "steel", 2f); se("fighting", "fairy", 0.5f)

        // Poison attacking
        se("poison", "poison", 0.5f); se("poison", "ground", 0.5f); se("poison", "rock", 0.5f)
        se("poison", "ghost", 0.5f); se("poison", "steel", 0f); se("poison", "grass", 2f)
        se("poison", "fairy", 2f)

        // Ground attacking
        se("ground", "fire", 2f); se("ground", "electric", 2f); se("ground", "grass", 0.5f)
        se("ground", "poison", 2f); se("ground", "flying", 0f); se("ground", "bug", 0.5f)
        se("ground", "rock", 2f); se("ground", "steel", 2f)

        // Flying attacking
        se("flying", "electric", 0.5f); se("flying", "grass", 2f); se("flying", "fighting", 2f)
        se("flying", "bug", 2f); se("flying", "rock", 0.5f); se("flying", "steel", 0.5f)

        // Psychic attacking
        se("psychic", "fighting", 2f); se("psychic", "poison", 2f); se("psychic", "psychic", 0.5f)
        se("psychic", "dark", 0f); se("psychic", "steel", 0.5f)

        // Bug attacking
        se("bug", "fire", 0.5f); se("bug", "grass", 2f); se("bug", "fighting", 0.5f)
        se("bug", "poison", 0.5f); se("bug", "flying", 0.5f); se("bug", "psychic", 2f)
        se("bug", "ghost", 0.5f); se("bug", "dark", 2f); se("bug", "steel", 0.5f)
        se("bug", "fairy", 0.5f)

        // Rock attacking
        se("rock", "fire", 2f); se("rock", "ice", 2f); se("rock", "fighting", 0.5f)
        se("rock", "ground", 0.5f); se("rock", "flying", 2f); se("rock", "bug", 2f)
        se("rock", "steel", 0.5f)

        // Ghost attacking
        se("ghost", "normal", 0f); se("ghost", "psychic", 2f); se("ghost", "ghost", 2f)
        se("ghost", "dark", 0.5f)

        // Dragon attacking
        se("dragon", "dragon", 2f); se("dragon", "steel", 0.5f); se("dragon", "fairy", 0f)

        // Dark attacking
        se("dark", "fighting", 0.5f); se("dark", "psychic", 2f); se("dark", "ghost", 2f)
        se("dark", "dark", 0.5f); se("dark", "fairy", 0.5f)

        // Steel attacking
        se("steel", "fire", 0.5f); se("steel", "water", 0.5f); se("steel", "electric", 0.5f)
        se("steel", "ice", 2f); se("steel", "rock", 2f); se("steel", "steel", 0.5f)
        se("steel", "fairy", 2f)

        // Fairy attacking
        se("fairy", "fire", 0.5f); se("fairy", "poison", 0.5f); se("fairy", "fighting", 2f)
        se("fairy", "dragon", 2f); se("fairy", "dark", 2f); se("fairy", "steel", 0.5f)

        return c
    }
}
