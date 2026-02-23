package com.cobbledex.network

import com.cobbledex.DebugLog
import com.cobbledex.JobRule
import com.cobbledex.RecipeViewerReloader
import com.cobbledex.SpawnDataIndex
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

/**
 * Receives the job_sync packet sent by CobbleCrew (server-side).
 * Payload ID matches CobbleCrew's namespace so the server's packet
 * is routed to this handler. CobbleDex does NOT need CobbleCrew on
 * the client classpath — just the matching payload ID.
 */
class CobbleworkersJobSyncPayload(val data: ByteArray) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<CobbleworkersJobSyncPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<CobbleworkersJobSyncPayload>(
            ResourceLocation.fromNamespaceAndPath("cobblecrew", "job_sync")
        )

        private const val MAX_PAYLOAD_SIZE = 1_048_576

        val CODEC: StreamCodec<FriendlyByteBuf, CobbleworkersJobSyncPayload> = StreamCodec.of(
            { buf: FriendlyByteBuf, payload: CobbleworkersJobSyncPayload -> buf.writeByteArray(payload.data) },
            { buf: FriendlyByteBuf -> CobbleworkersJobSyncPayload(buf.readByteArray(MAX_PAYLOAD_SIZE)) }
        )

        private val gson = Gson()

        fun applyJobRules(data: ByteArray) {
            val json = String(data, Charsets.UTF_8)
            val type = object : TypeToken<List<JobRuleWire>>() {}.type
            val wireRules: List<JobRuleWire> = gson.fromJson(json, type)
            val rules = wireRules.map { it.toJobRule() }

            SpawnDataIndex.applyJobRules(rules)
            DebugLog.info("Received ${rules.size} job rules from CobbleCrew")
        }
    }
}

/**
 * Wire format matching CobbleworkersApi.JobRule JSON structure.
 * Kept separate from CobbleDex's JobRule to avoid tight coupling.
 */
private data class JobRuleWire(
    val id: String = "",
    val displayName: String = "",
    val description: String = "",
    val enabled: Boolean = true,
    val requiredType: String? = null,
    val designatedSpecies: List<String> = emptyList(),
    val requiredMoves: List<String> = emptyList(),
    val requiredAbility: String? = null,
    val hardcodedSpecies: List<String> = emptyList(),
    val hardcodedSpeciesEnabled: Boolean = false,
    val priority: String = "",
) {
    fun toJobRule() = JobRule(
        id = id,
        displayName = displayName,
        description = description,
        enabled = enabled,
        requiredType = requiredType,
        designatedSpecies = designatedSpecies,
        requiredMoves = requiredMoves,
        requiredAbility = requiredAbility,
        hardcodedSpecies = hardcodedSpecies,
        hardcodedSpeciesEnabled = hardcodedSpeciesEnabled,
        priority = priority,
    )
}
