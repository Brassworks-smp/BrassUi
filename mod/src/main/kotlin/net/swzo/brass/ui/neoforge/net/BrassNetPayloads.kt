package net.swzo.brass.ui.neoforge.net

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.swzo.brass.ui.kit.net.BrassJson

/**
 * The whole action bus rides on a handful of payloads: requests up, replies and state down, and the
 * permission sync. JSON travels compressed via [CompressedJsonCodec] (gzip only kicks in past
 * [BrassJson.COMPRESS_THRESHOLD]), because the core registry already defines every action's shape - a
 * payload per action would duplicate that knowledge in the transport and tie the toolkit to one
 * loader's codec system.
 *
 * A null JSON value is encoded as an empty string (see the payload constructors), so `Unit` actions
 * with no input travel as an empty body rather than a sentinel byte.
 */
private const val MAX_JSON_LENGTH = 1_048_576

/**
 * A JSON string on the wire: length-prefixed bytes produced by [BrassJson.compress]. Decoding caps the
 * **decompressed** size so a hostile peer cannot run the server out of memory with a gzip bomb.
 */
private object CompressedJsonCodec : StreamCodec<ByteBuf, String> {

    override fun encode(buffer: ByteBuf, value: String) {
        val bytes = BrassJson.compress(value)
        buffer.writeInt(bytes.size)
        buffer.writeBytes(bytes)
    }

    override fun decode(buffer: ByteBuf): String {
        val bytes = ByteArray(buffer.readInt())
        buffer.readBytes(bytes)
        val json = BrassJson.decompress(bytes)
        check(json.length <= MAX_JSON_LENGTH) { "Decoded payload exceeds $MAX_JSON_LENGTH chars" }
        return json
    }
}

/** Client -> server: "run the action with this JSON input". Carries the protocol [version]. */
data class BrassActionPayload(
    val requestId: Long,
    val version: Int,
    val actionId: String,
    val json: String,
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<BrassActionPayload>(
            ResourceLocation.fromNamespaceAndPath("brassui", "action"),
        )
        val CODEC: StreamCodec<ByteBuf, BrassActionPayload> = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, BrassActionPayload::requestId,
            ByteBufCodecs.VAR_INT, BrassActionPayload::version,
            ByteBufCodecs.STRING_UTF8, BrassActionPayload::actionId,
            CompressedJsonCodec, BrassActionPayload::json,
            ::BrassActionPayload,
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}

/** Server -> client: the result of one action request, addressed by [requestId]. */
data class BrassReplyPayload(
    val requestId: Long,
    val json: String,
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<BrassReplyPayload>(
            ResourceLocation.fromNamespaceAndPath("brassui", "reply"),
        )
        val CODEC: StreamCodec<ByteBuf, BrassReplyPayload> = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, BrassReplyPayload::requestId,
            CompressedJsonCodec, BrassReplyPayload::json,
            ::BrassReplyPayload,
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}

/** Server -> client: a state value changed; subscribers for [stateId] update. */
data class BrassStatePayload(
    val stateId: String,
    val json: String,
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<BrassStatePayload>(
            ResourceLocation.fromNamespaceAndPath("brassui", "state"),
        )
        val CODEC: StreamCodec<ByteBuf, BrassStatePayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BrassStatePayload::stateId,
            CompressedJsonCodec, BrassStatePayload::json,
            ::BrassStatePayload,
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}

/** Client -> server: "I am now watching [stateId] - send me its current value." */
data class BrassSubscribePayload(
    val stateId: String,
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<BrassSubscribePayload>(
            ResourceLocation.fromNamespaceAndPath("brassui", "subscribe"),
        )
        val CODEC: StreamCodec<ByteBuf, BrassSubscribePayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BrassSubscribePayload::stateId,
            ::BrassSubscribePayload,
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}

/**
 * Client -> server: "send me your permission decisions for every registered action."
 *
 * An `object`, not a class: the codec is [StreamCodec.unit], and NeoForge's unit codec rejects any
 * payload instance that is not the **same object** it was built with. A singleton guarantees the
 * instance sent by the transport is the instance the codec expects.
 */
object BrassPermsRequestPayload : CustomPacketPayload {

    val TYPE = CustomPacketPayload.Type<BrassPermsRequestPayload>(
        ResourceLocation.fromNamespaceAndPath("brassui", "perms_request"),
    )
    val CODEC: StreamCodec<ByteBuf, BrassPermsRequestPayload> = StreamCodec.unit(BrassPermsRequestPayload)

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}

/** Server -> client: `actionId -> encoded decision` as a JSON map (see AuthDecision.encode). */
data class BrassPermsPayload(
    val json: String,
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<BrassPermsPayload>(
            ResourceLocation.fromNamespaceAndPath("brassui", "perms"),
        )
        val CODEC: StreamCodec<ByteBuf, BrassPermsPayload> = StreamCodec.composite(
            CompressedJsonCodec, BrassPermsPayload::json,
            ::BrassPermsPayload,
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
