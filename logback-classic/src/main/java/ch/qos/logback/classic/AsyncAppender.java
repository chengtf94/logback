package ch.qos.logback.classic;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AsyncAppenderBase;

/**
 * 异步追加器
 *
 *
 * @author Ceki G&uuml;lc&uuml;
 * @since 1.0.4
 */
public class AsyncAppender extends AsyncAppenderBase<ILoggingEvent> {

    boolean includeCallerData = false;

    @Override
    protected boolean isDiscardable(ILoggingEvent event) {
        Level level = event.getLevel();
        return level.toInt() <= Level.INFO_INT;
    }

    @Override
    protected void preprocess(ILoggingEvent eventObject) {
        eventObject.prepareForDeferredProcessing();
        if (includeCallerData)
            eventObject.getCallerData();
    }

    public boolean isIncludeCallerData() {
        return includeCallerData;
    }

    public void setIncludeCallerData(boolean includeCallerData) {
        this.includeCallerData = includeCallerData;
    }

}
