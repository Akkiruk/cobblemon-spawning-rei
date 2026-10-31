package com.cobbledex

import com.cobbledex.sync.ServerOverlay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FormRouterTest {

    private val tauros = listOf(
        FormRouter.FormRef("tauros_paldea_combat", "paldea-combat", setOf("paldean")),
        FormRouter.FormRef("tauros_paldea_blaze", "paldea-blaze", setOf("blaze-breed", "paldean")),
        FormRouter.FormRef("tauros_paldea_aqua", "paldea-aqua", setOf("paldean", "aqua-breed")),
    )

    private val golett = listOf(
        FormRouter.FormRef("golett_copper", "copper", setOf("copper")),
        FormRouter.FormRef("golett_exposed_copper", "exposed-copper", setOf("exposed_copper")),
    )

    private val meowstic = listOf(
        FormRouter.FormRef("meowstic_midnight", "midnight", setOf("midnight")),
        FormRouter.FormRef("meowstic_mega_midnight", "mega-midnight", setOf("mega_y", "midnight")),
    )

    private val meloetta = listOf(
        FormRouter.FormRef("meloetta_lament", "lament", setOf("lament-forme")),
        FormRouter.FormRef("meloetta_soprano", "soprano", setOf("soprano-forme")),
    )

    private fun spawn(
        pokemon: String,
        formAspects: String = "",
        biomes: List<String> = emptyList(),
        anticondition: SpawnAntiCondition? = null,
        herd: HerdContext? = null,
    ) = SpawnInfo.minimal(
        id = "$pokemon $formAspects", pokemon = pokemon, bucket = "common", weight = 1f,
        levelRange = "5-10", formAspects = formAspects, herd = herd,
    ).copy(biomes = biomes, anticondition = anticondition)

    private fun form(name: String, base: String, formName: String, vararg aspects: String) =
        EvolutionDataLoader.SpeciesBasicInfo(
            name = name, nationalDexNumber = 1, primaryType = "normal", secondaryType = null,
            catchRate = 45, weight = 1f, height = 1f,
            baseSpeciesName = base, formName = formName, formAspects = aspects.toSet(),
        )

    @Test
    fun theMostSpecificFormWinsWhenSeveralFit() {
        assertEquals("tauros_paldea_combat", FormRouter.formFor(tauros, "paldean bull_breed=combat")?.key)
        assertEquals("tauros_paldea_blaze", FormRouter.formFor(tauros, "paldean bull_breed=blaze")?.key)
        assertEquals("tauros_paldea_aqua", FormRouter.formFor(tauros, "paldean bull_breed=aqua")?.key)
    }

    @Test
    fun aChoiceFeatureFindsTheAspectItsFormatProduces() {
        assertEquals("meloetta_lament", FormRouter.formFor(meloetta, "lasers_song_forme=lament")?.key)
        assertEquals("meloetta_soprano", FormRouter.formFor(meloetta, "lasers_song_forme=soprano")?.key)
    }

    @Test
    fun aBareAspectMustMatchWholeNotAsAPartOfAnother() {
        assertEquals("golett_copper", FormRouter.formFor(golett, "copper")?.key)
        assertEquals("golett_exposed_copper", FormRouter.formFor(golett, "exposed_copper")?.key)
    }

    @Test
    fun aFormNeedingAnAspectTheSpawnLacksIsNotChosen() {
        assertEquals("meowstic_midnight", FormRouter.formFor(meowstic, "midnight")?.key)
    }

    @Test
    fun anExplicitFormNameIsHonoured() {
        assertEquals("tauros_paldea_aqua", FormRouter.formFor(tauros, "form=paldea-aqua")?.key)
        assertEquals("tauros_paldea_aqua", FormRouter.formFor(tauros, "Paldea-Aqua")?.key)
    }

    @Test
    fun aFlagFeatureCountsAsItsAspect() {
        assertEquals("tauros_paldea_combat", FormRouter.formFor(tauros, "paldean=true")?.key)
    }

    @Test
    fun propertiesThatDoNotChooseAFormAreIgnored() {
        val terapagos = listOf(FormRouter.FormRef("terapagos_stellar", "stellar", setOf("stellar-form")))
        assertNull(FormRouter.formFor(terapagos, "tera_type=stellar"))
    }

    @Test
    fun aVariantWithNoMatchingFormStaysUnrouted() {
        assertNull(FormRouter.formFor(tauros, "vivillon_wings=ocean"))
        assertNull(FormRouter.formFor(tauros, ""))
    }

    @Test
    fun routeMovesFormSpawnsOffTheBasePage() {
        val spawns = mapOf(
            "tauros" to listOf(spawn("tauros"), spawn("tauros", "paldean bull_breed=blaze")),
            "pikachu" to listOf(spawn("pikachu", "shiny=yes")),
        )
        val species = mapOf(
            "tauros_paldea_blaze" to form("tauros_paldea_blaze", "tauros", "paldea-blaze", "paldean", "blaze-breed"),
        )

        val routed = FormRouter.route(spawns, FormRouter.formsByBase(species)) { it.formAspects }

        assertEquals(listOf(""), routed["tauros"]?.map { it.formAspects })
        assertEquals(listOf("paldean bull_breed=blaze"), routed["tauros_paldea_blaze"]?.map { it.formAspects })
        assertEquals(1, routed["pikachu"]?.size)
    }

    @Test
    fun anythingWithAPropertyStringRoutesTheSameWay() {
        val fossils = mapOf(
            "tauros" to listOf(FossilCombo("tauros", listOf("a:b"), "form=paldea-blaze"), FossilCombo("tauros", listOf("a:c"))),
        )
        val species = mapOf(
            "tauros_paldea_blaze" to form("tauros_paldea_blaze", "tauros", "paldea-blaze", "paldean", "blaze-breed"),
        )

        val routed = FormRouter.route(fossils, FormRouter.formsByBase(species)) { it.extraTags }

        assertEquals(listOf(listOf("a:b")), routed["tauros_paldea_blaze"]?.map { it.fossilItems })
        assertEquals(listOf(listOf("a:c")), routed["tauros"]?.map { it.fossilItems })
    }

    @Test
    fun herdsThatDifferOnlyByFormAreDifferentRosters() {
        fun herd(aspects: String) = HerdContext(
            listOf(HerdMemberRef("tauros", aspects, HerdRole.ANY, false, null, null, 4, 1f)), 4,
        )

        assertNotEquals(herd("paldean bull_breed=blaze").rosterKey(), herd("paldean bull_breed=aqua").rosterKey())
    }

    @Test
    fun anAlphaDoesNotMakeANewRoster() {
        fun herd(aspects: String) = HerdContext(
            listOf(HerdMemberRef("tauros", aspects, HerdRole.ANY, false, null, null, 4, 1f)), 4,
        )

        assertEquals(herd("paldean").rosterKey(), herd("alpha paldean").rosterKey())
    }

    @Test
    fun aSpawnOnItsOwnFormPageIsNotLabelledWithThatForm() {
        val routed = spawn("tauros", "paldean bull_breed=blaze").copy(onOwnFormPage = true)

        assertEquals(emptyList(), SpawnDisplayHelper.mergeVariantSpawns(listOf(routed)).single().formVariants)
    }

    @Test
    fun aVariantLabelNamesEachWordOnce() {
        assertEquals("Alolan", SpawnDisplayHelper.formatFormAspects("alolan alolan=true"))
    }

    private fun missingNullscape(id: String) = id.removePrefix("#").startsWith("nullscape:")

    @Test
    fun anExclusionNamingAMissingModDoesNotHideTheSpawn() {
        val lament = spawn(
            "meloetta",
            biomes = listOf("#cobblemon:is_end"),
            anticondition = SpawnAntiCondition(biomes = listOf("nullscape:void_barrens", "nullscape:shadowlands")),
        )

        val kept = assertNotNull(ModFilter.withoutMissing(lament, ::missingNullscape))

        assertEquals(listOf("#cobblemon:is_end"), kept.biomes)
        assertNull(kept.anticondition)
    }

    @Test
    fun anExclusionKeepsTheEntriesThatStillApply() {
        val anti = SpawnAntiCondition(biomes = listOf("nullscape:shadowlands", "minecraft:the_end"))
        val kept = ModFilter.withoutMissing(spawn("meloetta", anticondition = anti), ::missingNullscape)

        assertEquals(listOf("minecraft:the_end"), kept?.anticondition?.biomes)
    }

    @Test
    fun aSpawnLosesOnlyTheBiomesFromMissingMods() {
        val mixed = spawn("meloetta", biomes = listOf("nullscape:crystalpeaks", "minecraft:the_end"))

        assertEquals(listOf("minecraft:the_end"), ModFilter.withoutMissing(mixed, ::missingNullscape)?.biomes)
    }

    @Test
    fun aSpawnWithOnlyMissingBiomesIsHidden() {
        val modOnly = spawn("meloetta", biomes = listOf("nullscape:void_barrens", "#nullscape:peaks"))

        assertNull(ModFilter.withoutMissing(modOnly, ::missingNullscape))
    }

    @Test
    fun aWeightBonusOnlyForAMissingBiomeIsDropped() {
        val shadowlands = "nullscape:shadowlands"
        val dead = WeightMultiplier(2f, conditionParts = listOf(WeightConditionPart(type = "biomes", ids = listOf(shadowlands))))
        val live = WeightMultiplier(3f, conditionParts = listOf(WeightConditionPart(type = "biomes", ids = listOf(shadowlands, "minecraft:the_end"))))
        val rain = WeightMultiplier(4f, conditionParts = listOf(WeightConditionPart(type = "rain")))
        val kept = ModFilter.withoutMissing(spawn("meloetta").copy(weightMultipliers = listOf(dead, live, rain)), ::missingNullscape)

        assertEquals(listOf(3f, 4f), kept?.weightMultipliers?.map { it.multiplier })
        assertEquals(listOf("minecraft:the_end"), kept?.weightMultipliers?.first()?.conditionParts?.first()?.ids)
    }

    @Test
    fun onACompanionServerOnlyTheServersNamespacesCount() {
        ServerDataView.set(ServerOverlay(1L, emptyMap(), namespaces = setOf("nullscape")))
        try {
            ModFilter.refresh()

            assertEquals(false, ModFilter.idMissing("nullscape:shadowlands"))
            assertEquals(true, ModFilter.idMissing("terralith:yellowstone"))
            assertEquals(false, ModFilter.idMissing("minecraft:plains"))
        } finally {
            ServerDataView.set(null)
        }
    }
}
