package com.cobbledex.sync

import net.minecraft.resources.ResourceLocation

/**
 * Constants of the server-to-client data sync (docs/SERVER_COMPANION.md).
 *
 * Everything under `com.cobbledex.sync` and `com.cobbledex.server` runs on dedicated servers, so
 * nothing in these packages may touch a `net.minecraft.client` class (ServerSideGuardTest enforces it).
 */
object SyncProtocol {
    /** Wire protocol version. It is also in every channel id: a new layout gets a new id, never a new layout under an old one. */
    const val VERSION = 1

    const val NAMESPACE = "cobbledex"

    /** Measured: ~2 MiB is where the server's own encoder throws and kicks the player. Stay far below. */
    const val MAX_CHUNK_BYTES = 512 * 1024
    const val DEFAULT_CHUNK_BYTES = 256 * 1024

    /** Vanilla caps client-to-server custom payloads at 32767 bytes; a request must stay under it. */
    const val MAX_REQUEST_BODY_BYTES = 24 * 1024

    /** Hard ceilings the client applies to whatever a server claims it is about to send. */
    const val MAX_COMPRESSED_BYTES = 24 * 1024 * 1024
    const val MAX_RAW_BYTES = 64 * 1024 * 1024
    const val MAX_CHUNKS = MAX_COMPRESSED_BYTES / 1024

    const val STATUS_UNCHANGED = 0
    const val STATUS_INCOMING = 1
    const val STATUS_REFUSED = 2

    /** A transfer the client has not finished within this long is dropped. */
    const val TRANSFER_TIMEOUT_MS = 30_000L

    /** The data folders mirrored from the server, each a directory of the same name under `data/<namespace>/`. */
    val CATEGORIES: List<String> = listOf(
        "species",
        "species_additions",
        "spawn_pool_world",
        "spawn_detail_presets",
        "habitat_pools",
    )

    val REQUEST_ID: ResourceLocation = id("sync_request_v$VERSION")
    val HEADER_ID: ResourceLocation = id("sync_header_v$VERSION")
    val CHUNK_ID: ResourceLocation = id("sync_chunk_v$VERSION")

    private fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(NAMESPACE, path)
}
