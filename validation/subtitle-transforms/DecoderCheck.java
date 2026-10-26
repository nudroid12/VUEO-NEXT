import com.vueo.shared.core.player.SubtitleCipherDecoder;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class DecoderCheck {
 static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }
 static void check(boolean v) { if (!v) throw new AssertionError(); }
 static String encrypt(String s, String key, String iv) throws Exception {
  Cipher c=Cipher.getInstance("AES/CBC/PKCS5Padding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(bytes(key),"AES"),new IvParameterSpec(bytes(iv)));
  return java.util.Base64.getEncoder().encodeToString(c.doFinal(bytes(s)));
 }
 static byte[] decode(byte[] data) throws Exception {
  return SubtitleCipherDecoder.decode(data,java.util.Arrays.asList(b64("0123456789abcdef"),b64("0011223344556677")),java.util.Arrays.asList(b64("fedcba9876543210"),b64("7766554433221100")));
 }
 static String b64(String s) {return java.util.Base64.getEncoder().encodeToString(bytes(s));}
 public static void main(String[] args) throws Exception {
  for (String[] pair : new String[][]{{"0123456789abcdef","fedcba9876543210"},{"0011223344556677","7766554433221100"}}) {
   String plain="Hello, selamat datang!", encoded=encrypt(plain,pair[0],pair[1]);
   String source="1\r\n00:00:01,000 --> 00:00:03,000\r\n"+encoded+"\r\n\r\n2\r\n00:00:04,000 --> 00:00:05,000\r\nAlready plain.\r\n";
   byte[] decoded=decode(bytes(source));String output=new String(decoded,StandardCharsets.UTF_8);
   check(output.contains(plain)&&output.contains("Already plain.")&&!output.contains(encoded)&&output.contains("00:00:01,000 --> 00:00:03,000"));
   check(Arrays.equals(decoded,decode(decoded)));
  }
  String normal="WEBVTT\n\n00:01.000 --> 00:03.000\nPlain captions.";
  check(Arrays.equals(bytes(normal),decode(bytes(normal))));
  String cipher=encrypt("Hello", "xxxxxxxxxxxxxxxx", "yyyyyyyyyyyyyyyy");
  try { decode(bytes("1\n00:00:01,000 --> 00:00:03,000\n"+cipher));throw new AssertionError("Ciphertext escaped"); } catch(java.io.IOException expected) {}
  try { decode(new byte[2*1024*1024+1]);throw new AssertionError(); } catch(java.io.IOException expected) {}
  System.out.println("PASS provider-declared key/IV pairs, timestamp preservation, mixed plaintext, idempotent cached text, VTT plaintext, wrong key rejection, size bound");
 }
}
