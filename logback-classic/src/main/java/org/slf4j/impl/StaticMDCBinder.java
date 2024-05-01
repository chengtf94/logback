package org.slf4j.impl;

import org.slf4j.spi.MDCAdapter;

import ch.qos.logback.classic.util.LogbackMDCAdapter;

/**
 * MDC绑定适配器
 *
 * @author Ceki G&uuml;lc&uuml;
 */
public class StaticMDCBinder {
    private StaticMDCBinder() {
    }

    public static final StaticMDCBinder SINGLETON = new StaticMDCBinder();

    public MDCAdapter getMDCA() {
        return new LogbackMDCAdapter();
    }

    public String getMDCAdapterClassStr() {
        return LogbackMDCAdapter.class.getName();
    }

}
