//package dx;
//
//import java.io.UnsupportedEncodingException;
//import java.security.InvalidAlgorithmParameterException;
//import java.security.InvalidKeyException;
//import java.security.NoSuchAlgorithmException;
//import java.util.Base64;
//
//import javax.crypto.BadPaddingException;
//import javax.crypto.Cipher;
//import javax.crypto.IllegalBlockSizeException;
//import javax.crypto.NoSuchPaddingException;
//import javax.crypto.spec.IvParameterSpec;
//import javax.crypto.spec.SecretKeySpec;
//
///**
// * create by yekangqi
// * <hr>
// * time: 2026/8/5 17:47
// * <hr>
// * description:
// */
//public class Demo {
//
//    public static void main(String[] args) throws Exception {
//        testGetAuth();
//
//    }
//
//    private static void demo() throws Exception {
//        String cipherText = "q2ser383+jU8QnIoD1/RMXIaGD7z2gKxTL5iPtwdeU1GXvIJ8pHrZb1yhl2MZxShB4r/5Id8ShNQVm15LSv7VPrmPs6j2uCsZl/w4wZ5XJI=";
//        // 解析出的 Key 和 IV
//        String key = "RLO6nUNOHe0ht1~w";//临时的key
//        String iv = "2747915629166172";//临时的iv
//
//        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");
//        IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes("UTF-8"));
//
//        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
//        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
//
//        byte[] decodedCipherText = Base64.getDecoder().decode(cipherText);
//        byte[] decryptedBytes = cipher.doFinal(decodedCipherText);
//
//        System.out.println("解密结果: " + new String(decryptedBytes, "UTF-8"));
//    }
//
//    private static void testGetAuth() throws Exception {
//        String cipherText = "LvVaedwbnYzTKi6lQQk9+c0w97gft0JIcmsJGvmYiG4oaVdgmCn+fLF3BbiYE9+sFA3kLaEPC1Vjhjw57LeZ+sei5iXLn/l7sBpw7o0LMNxFwvjHma+3SPzlXNESUyCNm+o5f6BasE/SqpM14Jj6j7yTSOwmQBRkdcPgEBiJ6xY=";
//        String dateBase64Str = Base64.getEncoder().encodeToString("2026-08-06 09:53:24".getBytes("UTF-8"));
//        // 解析出的 Key 和 IV
//        String key = dateBase64Str.substring(1,17);//临时的key
//        String iv = dateBase64Str.substring(2,18);//临时的iv
//
//        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");
//        IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes("UTF-8"));
//
//        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
//        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
//
//        byte[] decodedCipherText = Base64.getDecoder().decode(cipherText);
//        byte[] decryptedBytes = cipher.doFinal(decodedCipherText);
//
//        System.out.println("解密结果: " + new String(decryptedBytes, "UTF-8"));
//    }
//
//}
