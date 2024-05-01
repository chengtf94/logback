package ch.qos.logback.classic;

import static ch.qos.logback.core.CoreConstants.EVALUATOR_MAP;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.status.ErrorStatus;
import ch.qos.logback.core.status.InfoStatus;
import org.slf4j.ILoggerFactory;
import org.slf4j.Marker;

import ch.qos.logback.classic.spi.LoggerComparator;
import ch.qos.logback.classic.spi.LoggerContextListener;
import ch.qos.logback.classic.spi.LoggerContextVO;
import ch.qos.logback.classic.spi.TurboFilterList;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.classic.util.LoggerNameUtil;
import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.boolex.EventEvaluator;
import ch.qos.logback.core.spi.FilterReply;
import ch.qos.logback.core.spi.LifeCycle;
import ch.qos.logback.core.spi.SequenceNumberGenerator;
import ch.qos.logback.core.status.StatusListener;
import ch.qos.logback.core.status.StatusManager;
import ch.qos.logback.core.status.WarnStatus;
import org.slf4j.spi.MDCAdapter;

/**
 * 日志上下文
 *
 * @author Ceki Gulcu
 */
public class LoggerContext extends ContextBase implements ILoggerFactory, LifeCycle {

    /** Default setting of packaging data in stack traces */
    public static final boolean DEFAULT_PACKAGING_DATA = false;
    final Logger root;
    private int size;
    private int noAppenderWarning = 0;
    final private List<LoggerContextListener> loggerContextListenerList = new ArrayList<LoggerContextListener>();
    private Map<String, Logger> loggerCache;

    private LoggerContextVO loggerContextRemoteView;
    private final TurboFilterList turboFilterList = new TurboFilterList();
    private boolean packagingDataEnabled = DEFAULT_PACKAGING_DATA;
    SequenceNumberGenerator sequenceNumberGenerator = null; // by default there is no SequenceNumberGenerator
    MDCAdapter mdcAdapter;
    private int maxCallerDataDepth = ClassicConstants.DEFAULT_MAX_CALLEDER_DATA_DEPTH;
    int resetCount = 0;
    private List<String> frameworkPackages;

    public LoggerContext() {
        super();
        this.loggerCache = new ConcurrentHashMap<String, Logger>();
        this.loggerContextRemoteView = new LoggerContextVO(this);
        this.root = new Logger(Logger.ROOT_LOGGER_NAME, null, this);
        this.root.setLevel(Level.DEBUG);
        loggerCache.put(Logger.ROOT_LOGGER_NAME, root);
        initEvaluatorMap();
        size = 1;
        this.frameworkPackages = new ArrayList<String>();
    }
    void initEvaluatorMap() {
        putObject(EVALUATOR_MAP, new HashMap<String, EventEvaluator<?>>());
    }

    @Override
    public void setName(String name) {
        super.setName(name);
        updateLoggerContextVO();
    }
    private void updateLoggerContextVO() {
        loggerContextRemoteView = new LoggerContextVO(this);
    }

    @Override
    public void putProperty(String key, String val) {
        super.putProperty(key, val);
        updateLoggerContextVO();
    }


    @Override
    public void start() {
        super.start();
        fireOnStart();
    }

    /**
     * 获取Logger
     */
    public final Logger getLogger(final Class<?> clazz) {
        return getLogger(clazz.getName());
    }
    @Override
    public Logger getLogger(final String name) {
        if (name == null) {
            throw new IllegalArgumentException("name argument cannot be null");
        }
        if (Logger.ROOT_LOGGER_NAME.equalsIgnoreCase(name)) {
            return root;
        }
        int i = 0;
        Logger logger = root;
        Logger childLogger = (Logger) loggerCache.get(name);
        if (childLogger != null) {
            return childLogger;
        }
        // if the desired logger does not exist, them create all the loggers
        // in between as well (if they don't already exist)
        String childName;
        while (true) {
            int h = LoggerNameUtil.getSeparatorIndexOf(name, i);
            if (h == -1) {
                childName = name;
            } else {
                childName = name.substring(0, h);
            }
            // move i left of the last point
            i = h + 1;
            synchronized (logger) {
                childLogger = logger.getChildByName(childName);
                if (childLogger == null) {
                    childLogger = logger.createChildByName(childName);
                    loggerCache.put(childName, childLogger);
                    incSize();
                }
            }
            logger = childLogger;
            if (h == -1) {
                return childLogger;
            }
        }
    }

    private void incSize() {
        size++;
    }

    int size() {
        return size;
    }

    public Logger exists(String name) {
        return (Logger) loggerCache.get(name);
    }

    final void noAppenderDefinedWarning(final Logger logger) {
        if (noAppenderWarning++ == 0) {
            getStatusManager().add(new WarnStatus(
                    "No appenders present in context [" + getName() + "] for logger [" + logger.getName() + "].",
                    logger));
        }
    }

    public List<Logger> getLoggerList() {
        Collection<Logger> collection = loggerCache.values();
        List<Logger> loggerList = new ArrayList<Logger>(collection);
        Collections.sort(loggerList, new LoggerComparator());
        return loggerList;
    }

    public LoggerContextVO getLoggerContextRemoteView() {
        return loggerContextRemoteView;
    }

    public void setPackagingDataEnabled(boolean packagingDataEnabled) {
        this.packagingDataEnabled = packagingDataEnabled;
    }

    public boolean isPackagingDataEnabled() {
        return packagingDataEnabled;
    }

    private void cancelScheduledTasks() {
        for (ScheduledFuture<?> sf : scheduledFutures) {
            sf.cancel(false);
        }
        scheduledFutures.clear();
    }

    private void resetStatusListenersExceptResetResistant() {
        StatusManager sm = getStatusManager();
        for (StatusListener sl : sm.getCopyOfStatusListenerList()) {
            if(!sl.isResetResistant()) {
                sm.remove(sl);
            }
        }
    }

    public TurboFilterList getTurboFilterList() {
        return turboFilterList;
    }

    public void addTurboFilter(TurboFilter newFilter) {
        turboFilterList.add(newFilter);
    }

    /**
     * First processPriorToRemoval all registered turbo filters and then clear the
     * registration list.
     */
    public void resetTurboFilterList() {
        for (TurboFilter tf : turboFilterList) {
            tf.stop();
        }
        turboFilterList.clear();
    }

    final FilterReply getTurboFilterChainDecision_0_3OrMore(final Marker marker, final Logger logger, final Level level,
            final String format, final Object[] params, final Throwable t) {
        if (turboFilterList.size() == 0) {
            return FilterReply.NEUTRAL;
        }
        return turboFilterList.getTurboFilterChainDecision(marker, logger, level, format, params, t);
    }

    final FilterReply getTurboFilterChainDecision_1(final Marker marker, final Logger logger, final Level level,
            final String format, final Object param, final Throwable t) {
        if (turboFilterList.size() == 0) {
            return FilterReply.NEUTRAL;
        }
        return turboFilterList.getTurboFilterChainDecision(marker, logger, level, format, new Object[] { param }, t);
    }

    final FilterReply getTurboFilterChainDecision_2(final Marker marker, final Logger logger, final Level level,
            final String format, final Object param1, final Object param2, final Throwable t) {
        if (turboFilterList.size() == 0) {
            return FilterReply.NEUTRAL;
        }
        return turboFilterList.getTurboFilterChainDecision(marker, logger, level, format,
                new Object[] { param1, param2 }, t);
    }

    @Override
    public void stop() {
        reset();
        fireOnStop();
        resetAllListeners();
        super.stop();
    }

    @Override
    public void reset() {
        resetCount++;
        super.reset();
        initEvaluatorMap();
        initCollisionMaps();
        root.recursiveReset();
        resetTurboFilterList();
        cancelScheduledTasks();
        fireOnReset();
        resetListenersExceptResetResistant();
        resetStatusListenersExceptResetResistant();
    }

    private void fireOnReset() {
        for (LoggerContextListener listener : loggerContextListenerList) {
            listener.onReset(this);
        }
    }


    // === start listeners ==============================================
    public void addListener(LoggerContextListener listener) {
        loggerContextListenerList.add(listener);
    }

    public void removeListener(LoggerContextListener listener) {
        loggerContextListenerList.remove(listener);
    }

    private void resetListenersExceptResetResistant() {
        List<LoggerContextListener> toRetain = new ArrayList<LoggerContextListener>();

        for (LoggerContextListener lcl : loggerContextListenerList) {
            if (lcl.isResetResistant()) {
                toRetain.add(lcl);
            }
        }
        loggerContextListenerList.retainAll(toRetain);
    }

    private void resetAllListeners() {
        loggerContextListenerList.clear();
    }

    public List<LoggerContextListener> getCopyOfListenerList() {
        return new ArrayList<LoggerContextListener>(loggerContextListenerList);
    }

    void fireOnLevelChange(Logger logger, Level level) {
        for (LoggerContextListener listener : loggerContextListenerList) {
            listener.onLevelChange(logger, level);
        }
    }

    private void fireOnStart() {
        for (LoggerContextListener listener : loggerContextListenerList) {
            listener.onStart(this);
        }
    }

    private void fireOnStop() {
        for (LoggerContextListener listener : loggerContextListenerList) {
            listener.onStop(this);
        }
    }

    // === end listeners ==============================================

    @Override
    public String toString() {
        return this.getClass().getName() + "[" + getName() + "]";
    }

    public int getMaxCallerDataDepth() {
        return maxCallerDataDepth;
    }

    public void setMaxCallerDataDepth(int maxCallerDataDepth) {
        this.maxCallerDataDepth = maxCallerDataDepth;
    }

    public List<String> getFrameworkPackages() {
        return frameworkPackages;
    }

    @Override
    public void setSequenceNumberGenerator(SequenceNumberGenerator sng) {
        this.sequenceNumberGenerator = sng;
    }

    @Override
    public SequenceNumberGenerator getSequenceNumberGenerator() {
        return sequenceNumberGenerator;
    }

    public MDCAdapter getMDCAdapter() {
        return mdcAdapter;
    }

    public void setMDCAdapter(MDCAdapter anAdapter) {
        if(this.mdcAdapter ==  null) {
            this.mdcAdapter = anAdapter;
        } else {
            StatusManager sm = getStatusManager();
            sm.add(new ErrorStatus("mdcAdapter cannot be set multiple times", this, new IllegalStateException("mdcAdapter already set")));
        }
    }

}
