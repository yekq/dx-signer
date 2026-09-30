package dx.channel;

import java.util.Properties;

/**
 * create by yekangqi
 * <hr>
 * time: 2026/09/30 10:54
 * description: 在界面、配置和签名线程之间传递不可变的签名方案选项。
 */
public final class SigningOptions {
    public static final SigningOptions DEFAULT = new SigningOptions(true, true, false, false);
    private final boolean v1;
    private final boolean v2;
    private final boolean v3;
    private final boolean v4;

    public SigningOptions(boolean v1, boolean v2, boolean v3, boolean v4) {
        this.v1 = v1;
        this.v2 = v2;
        this.v3 = v3;
        this.v4 = v4;
    }

    public boolean isV1Enabled() {
        return v1;
    }

    public boolean isV2Enabled() {
        return v2;
    }

    public boolean isV3Enabled() {
        return v3;
    }

    public boolean isV4Enabled() {
        return v4;
    }

    public SigningOptions withoutV4() {
        return new SigningOptions(v1, v2, v3, false);
    }

    public void validate(boolean aab, boolean channels) {
        if (aab) {
            if (channels || !v1 || v3 || v4) {
                throw new IllegalArgumentException("AAB 请保留 V1，并关闭 V3/V4 和多渠道输出");
            }
            return;
        }
        if (!v1 && !v2 && !v3) {
            throw new IllegalArgumentException("请至少启用 V1、V2、V3 中的一种签名方案");
        }
        if (v4 && !v2 && !v3) {
            throw new IllegalArgumentException("V4 签名必须同时启用 V2 或 V3");
        }
        if (channels && !v2) {
            throw new IllegalArgumentException("多渠道的 Walle 信息写入需要启用 V2 签名");
        }
    }

    public static SigningOptions fromProperties(Properties properties) {
        return new SigningOptions(read(properties, "v1-signing-enabled", true),
                read(properties, "v2-signing-enabled", true),
                read(properties, "v3-signing-enabled", false),
                read(properties, "v4-signing-enabled", false));
    }

    public void writeTo(Properties properties) {
        properties.setProperty("v1-signing-enabled", Boolean.toString(v1));
        properties.setProperty("v2-signing-enabled", Boolean.toString(v2));
        properties.setProperty("v3-signing-enabled", Boolean.toString(v3));
        properties.setProperty("v4-signing-enabled", Boolean.toString(v4));
    }

    private static boolean read(Properties properties, String key, boolean defaultValue) {
        String value = properties.getProperty(key, Boolean.toString(defaultValue)).trim();
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new IllegalArgumentException(key + " 必须为 true 或 false");
        }
        return Boolean.parseBoolean(value);
    }
}
