package com.vueo.shared.core.player;

import android.util.Base64;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Explicit opt-in sidecar transform. No network, video interception or plaintext logging. */
public final class SubtitleCipherDecoder {
    private SubtitleCipherDecoder() {}
    private static final Pattern TIME = Pattern.compile(".*\\d{1,2}:\\d{2}[.,]\\d{1,3}\\s*-->.*");
    private static final Pattern ENCODED = Pattern.compile("[A-Za-z0-9+/]{20,}={0,2}");

    public static byte[] decode(byte[] bytes, List<String> keys, List<String> ivs) throws IOException {
        if (keys == null || ivs == null || keys.isEmpty() || keys.size() > 4 || keys.size() != ivs.size()) throw new IOException("Invalid subtitle transform configuration");
        if (bytes.length > 2 * 1024 * 1024) throw new IOException("Subtitle too large");
        final String source;
        try { source = utf8(bytes); }
        catch (Exception e) { throw new IOException("Invalid subtitle encoding", e); }
        String[] lines = source.split("\\r?\\n", -1);
        boolean cue = false, changed = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) { cue = false; continue; }
            if (TIME.matcher(line).matches()) { cue = true; continue; }
            if (!cue || line.length() % 4 != 0 || !ENCODED.matcher(line).matches()) continue;
            final byte[] encrypted;
            try { encrypted = Base64.decode(line, Base64.DEFAULT); }
            catch (IllegalArgumentException e) { throw new IOException("Invalid subtitle cipher text", e); }
            if (encrypted.length == 0 || encrypted.length % 16 != 0) continue;
            String plain = null;
            for (int key = 0; key < keys.size(); key++) {
                try {
                    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                    cipher.init(Cipher.DECRYPT_MODE,
                        new SecretKeySpec(Base64.decode(keys.get(key), Base64.DEFAULT), "AES"),
                        new IvParameterSpec(Base64.decode(ivs.get(key), Base64.DEFAULT)));
                    String candidate = utf8(cipher.doFinal(encrypted));
                    if (candidate.trim().isEmpty() || candidate.matches("(?s).*[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F].*")) continue;
                    plain = candidate;
                    break;
                } catch (Exception ignored) { /* Try the next provider-declared key. */ }
            }
            if (plain == null) throw new IOException("Subtitle decryption failed");
            lines[i] = plain;
            changed = true;
        }
        if (!changed) return bytes;
        StringBuilder output = new StringBuilder(source.length());
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) output.append('\n');
            output.append(lines[i]);
        }
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String utf8(byte[] bytes) throws Exception {
        return StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString();
    }
}
