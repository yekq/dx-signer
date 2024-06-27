package dx.signer;

import java.util.Properties;

/**
 * create by yekangqi
 * <hr>
 * time: 2024/6/25 9:31
 * <hr>
 * description: 签名配置
 */
public class SignerConfigBean {
    private boolean readOnly;
    private String in;//=D:\\Signer\\output\\
    private String ksPass;//=xx;//
    private String out;//=D:\\Signer\\output\\xxxxx.apk
    private String inFilename;//=
    private String ksKeyAlias;//={{auto}}
    private String channelList;//=
    private String keyPass;//=
    private String ks;//=D\:\\Android\\keystore\\test.jks

    public SignerConfigBean() {
    }

    public SignerConfigBean(Properties initConfig) {
        boolean readOnly = "true".equals(initConfig.getProperty("config-read-only", ""));
        String ks = initConfig.getProperty("ks", "");
        String inPath = initConfig.getProperty("in", "");
        String inFileName = initConfig.getProperty("in-filename", "");
        String outPath = initConfig.getProperty("out", "");
        String ksP = initConfig.getProperty("ks-pass", "");
        String keyP = initConfig.getProperty("key-pass", "");
        String channelList = initConfig.getProperty("channel-list", "");
        String ksAlias = initConfig.getProperty("ks-key-alias", "{{auto}}");

        setReadOnly(readOnly);
        setKs(ks);
        setIn(inPath);
        setKsPass(ksP);
        setInFilename(inFileName);
        setKsKeyAlias(ksAlias);
        setChannelList(channelList);
        setKeyPass(keyP);
        setOut(outPath);
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public String getIn() {
        return in;
    }

    public void setIn(String in) {
        this.in = in;
    }

    public String getKsPass() {
        return ksPass;
    }

    public void setKsPass(String ksPass) {
        this.ksPass = ksPass;
    }

    public String getOut() {
        return out;
    }

    public void setOut(String out) {
        this.out = out;
    }

    public String getInFilename() {
        return inFilename;
    }

    public void setInFilename(String inFilename) {
        this.inFilename = inFilename;
    }

    public String getKsKeyAlias() {
        return ksKeyAlias;
    }

    public void setKsKeyAlias(String ksKeyAlias) {
        this.ksKeyAlias = ksKeyAlias;
    }

    public String getChannelList() {
        return channelList;
    }

    public void setChannelList(String channelList) {
        this.channelList = channelList;
    }

    public String getKeyPass() {
        return keyPass;
    }

    public void setKeyPass(String keyPass) {
        this.keyPass = keyPass;
    }

    public String getKs() {
        return ks;
    }

    public void setKs(String ks) {
        this.ks = ks;
    }
}