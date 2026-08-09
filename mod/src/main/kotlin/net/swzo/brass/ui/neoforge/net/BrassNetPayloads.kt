package net.swzo.brass.ui.neoforge.net

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.swzo.brass.ui.kit.net.BrassJson

/**
 * The whole action bus rides on a handful of payloads: requests up, replies and state down, and the
 * permission sync. Bodies travel as **BSON bytes** (see [net.swzo.brass.ui.kit.net.BrassBson]),
 * length-prefixed and gzip-compressed past [BrassJson.COMPRESS_THRESHOLD] by [BsonBytesCodec] -
 * the core registry already defines every action's shape, so a payload per action would duplicate
 * that knowledge in the transport and tie the toolkit to one loader's codec system.
 *
 * A null BSON value is encoded as an empty byte array, so `Unit` actions with no input travel as an
 * empty body rather than a sentinel byte.
 */
private const val MAX_DATA_LENGTH = 1_048_576

/**
 * A BSON byte array on the wire: length-prefixed bytes produced by [BrassJson.compress]. Decoding
 * caps the **decompressed** size so a hostile peer cannot run the server out of memory with a gzip
 * bomb.
 */
private object BsonBytesCodec : StreamCodec<ByteBuf, ByteArray> {

    override fun encode(buffer: ByteBuf, value: ByteArray) {
        val bytes = BrassJson.compress(value)
        buffer.writeInt(bytes.size)
        buffer.writeBytes(bytes)
    }

    override fun decode(buffer: ByteBuf): ByteArray {
        val bytes = ByteArray(buffer.readInt())
        buffer.readBytes(bytes)
        val data = BrassJson.decompress(bytes)
        check(data.size <= MAX_DATA_LENGTH) { "Decoded payload exceeds $MAX_DATA_LENGTH bytes" }
        return data
    }
}

/** Client -> server: "run the action with this BSON input". Carries the protocol [version]. */
data class BrassActionPayload(
    val requestId: Long,
    val version: Int,
    val actionId: String,
    val data: ByteArray,
    /** Index of this piece when [chunks] > 1 (large actions travel chunked). */
    val chunk: Int = 0,
    /** Total piece count; 1 means the action travelled whole. */
    val chunks: Int = 1,
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<BrassActionPayload>(
            ResourceLocation.fromNamespaceAndPath("brassui", "action"),
        )
        val CODEC: StreamCodec<ByteBuf, BrassActionPayload> = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, BrassActionPayload::requestId,
            ByteBufCodecs.VAR_INT, BrassActionPayload::version,
            ByteBufCodecs.STRING_UTF8, BrassActionPayload::actionId,
            BsonBytesCodec, BrassActionPayload::data,
            ByteBufCodecs.VAR_INT, BrassActionPayload::chunk,
            ByteBufCodecs.VAR_INT, BrassActionPayload::chunks,
            ::BrassActionPayload,
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}

/** Server -> client: the result of one action request, addressed by [requestId]. */
data class BrassReplyPayload(
    val requestId: Long,
    val data: ByteArray,
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<BrassReplyPayload>(
            ResourceLocation.fromNamespaceAndPath("brassui", "reply"),
        )
        val CODEC: StreamCodec<ByteBuf, BrassReplyPayload> = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, BrassReplyPayload::requestId,
            BsonBytesCodec, BrassReplyPayload::data,
            ::BrassReplyPayload,
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}

/** Server -> client: a state value changed; subscribers for [stateId] update. */
data class BrassStatePayload(
    val stateId: String,
    val data: ByteArray,
    /** Index of this piece when [chunks] > 1 (large states travel chunked). */
    val chunk: Int = 0,
    /** Total piece count; 1 means the state travelled whole. */
    val chunks: Int = 1,
    /** Identity shared by every piece of one logical state update. */
    val transferId: Long = 0L,
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<BrassStatePayload>(
            ResourceLocation.fromNamespaceAndPath("brassui", "state"),
        )
        val CODEC: StreamCodec<ByteBuf, BrassStatePayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BrassStatePayload::stateId,
            BsonBytesCodec, BrassStatePayload::data,
            ByteBufCodecs.VAR_INT, BrassStatePayload::chunk,
            ByteBufCodecs.VAR_INT, BrassStatePayload::chunks,
            ByteBufCodecs.VAR_LONG, BrassStatePayload::transferId,
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

/** Server -> client: `actionId -> encoded decision` as a BSON map (see AuthDecision.encode). */
data class BrassPermsPayload(
    val data: ByteArray,
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<BrassPermsPayload>(
            ResourceLocation.fromNamespaceAndPath("brassui", "perms"),
        )
        val CODEC: StreamCodec<ByteBuf, BrassPermsPayload> = StreamCodec.composite(
            BsonBytesCodec, BrassPermsPayload::data,
            ::BrassPermsPayload,
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
