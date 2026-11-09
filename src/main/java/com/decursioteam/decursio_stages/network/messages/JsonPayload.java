package com.decursioteam.decursio_stages.network.messages;

import net.minecraft.network.FriendlyByteBuf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * GZIP helpers for sending JSON over the network. Serverbound custom payloads are capped at 32 KiB,
 * so restriction JSON (which can contain a lot of NBT) is compressed before being sent.
 */
public final class JsonPayload {
    public static final int MAX_BYTES = 1 << 21;

    private JsonPayload() {}

    public static byte[] compress(String json) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(json.getBytes(StandardCharsets.UTF_8));
            gzip.finish();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String decompress(byte[] data) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data))) {
            byte[] out = gzip.readNBytes(MAX_BYTES);
            return new String(out, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void write(FriendlyByteBuf buf, String json) {
        buf.writeByteArray(compress(json));
    }

    public static String read(FriendlyByteBuf buf) {
        return decompress(buf.readByteArray(MAX_BYTES));
    }
}
