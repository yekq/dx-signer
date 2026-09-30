package dx.signer;

/**
 * create by yekangqi
 * <hr>
 * time: 2026/09/30 04:35
 * description: 验证梆梆加固产物的签名输出文件名。
 */
public final class UXOutputFileNameTest {
    public static void main(String[] args) {
        assertOutput("未加固_测试Demo_V1.0_protected.8_用户试用包V2_0409.apk",
                "测试Demo_V1.0_用户试用包V2_0409.apk");
        assertOutput("未加固_测试Demo_V1.0_protected.apk", "测试Demo_V1.0.apk");
        assertOutput("测试Demo_V1.0_protected.8.apk", "测试Demo_V1.0.apk");
        assertOutput("测试Demo_V1.0_用户试用包V2_0409.apk",
                "测试Demo_V1.0_用户试用包V2_0409.apk");
        System.out.println("梆梆加固文件名测试通过");
    }

    private static void assertOutput(String input, String expected) {
        String actual = UX.deriveOutputFileName(input);
        if (!expected.equals(actual)) {
            throw new AssertionError("输出文件名不符，预期：" + expected + "，实际：" + actual);
        }
    }
}
