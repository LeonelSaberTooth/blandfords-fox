package net.maidens.core;

/** Whether the automatic game test is running: extra bookkeeping only it reads is skipped otherwise. */
public final class SelfTestFlag {
    public static final boolean ON = System.getProperty("maidens.selftest") != null;

    private SelfTestFlag() {}
}
