package com.vueo.shared.core.player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure playlist transformation. Network keys/segments remain handled by Media3. */
public final class FlixPlaylistCodec {
    private FlixPlaylistCodec() {}
    private static String attribute(String line, String key) {
        Matcher match = Pattern.compile("(?:[:,])" + key + "=(?:\"([^\"]*)\"|([^,]*))", Pattern.CASE_INSENSITIVE).matcher(line);
        return match.find() ? (match.group(1) != null ? match.group(1) : match.group(2)) : "";
    }
    public static byte[] decode(byte[] cipher, byte[] key) throws IOException {
        if (key.length != 32) throw new IOException("Invalid Flix playlist key");
        byte[] plain = new byte[cipher.length];
        for (int i = 0; i < plain.length; i++) plain[i] = (byte) (cipher[i] ^ key[i % key.length]);
        return filterJapanese(plain);
    }
    public static byte[] filterJapanese(byte[] bytes) throws IOException {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!text.startsWith("#EXTM3U")) throw new IOException("Invalid Flix HLS playlist");
        if (!text.contains("#EXT-X-STREAM-INF:")) return bytes;
        Set<String> groups = new HashSet<>();
        String[] lines = text.split("\\r?\\n");
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            if (line.startsWith("#EXT-X-MEDIA:") && attribute(line, "TYPE").equalsIgnoreCase("AUDIO")) {
                String language = attribute(line, "LANGUAGE").toLowerCase(Locale.ROOT);
                if (!language.matches("ja|jpn|japanese")) continue;
                groups.add(attribute(line, "GROUP-ID"));
                line = line.replaceAll("(?i),DEFAULT=(?:YES|NO)", "").replaceAll("(?i),AUTOSELECT=(?:YES|NO)", "") + ",DEFAULT=YES,AUTOSELECT=YES";
            }
            result.append(line).append('\n');
        }
        for (String line : lines) {
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                String group = attribute(line, "AUDIO");
                if (!group.isEmpty() && !groups.contains(group)) throw new IOException("Japanese Flix audio group unavailable");
            }
        }
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }
}
