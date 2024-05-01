package ch.qos.logback.classic.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.spi.MDCAdapter;

/**
 * MDC
 *
 * @author Ceki G&uuml;lc&uuml;
 */
public class LogbackMDCAdapter implements MDCAdapter {

    /** 基于ThreadLocal存储上下文、 */
    final ThreadLocal<Map<String, String>> copyOnThreadLocal = new ThreadLocal<Map<String, String>>();
    final ThreadLocal<Integer> lastOperation = new ThreadLocal<Integer>();
    private static final int WRITE_OPERATION = 1;
    private static final int MAP_COPY_OPERATION = 2;

    @Override
    public void put(String key, String val) throws IllegalArgumentException {
        if (key == null) {
            throw new IllegalArgumentException("key cannot be null");
        }
        Map<String, String> oldMap = copyOnThreadLocal.get();
        Integer lastOp = getAndSetLastOperation(WRITE_OPERATION);
        if (wasLastOpReadOrNull(lastOp) || oldMap == null) {
            Map<String, String> newMap = duplicateAndInsertNewMap(oldMap);
            newMap.put(key, val);
        } else {
            oldMap.put(key, val);
        }
    }
    private Integer getAndSetLastOperation(int op) {
        Integer lastOp = lastOperation.get();
        lastOperation.set(op);
        return lastOp;
    }
    private boolean wasLastOpReadOrNull(Integer lastOp) {
        return lastOp == null || lastOp.intValue() == MAP_COPY_OPERATION;
    }
    private Map<String, String> duplicateAndInsertNewMap(Map<String, String> oldMap) {
        Map<String, String> newMap = Collections.synchronizedMap(new HashMap<String, String>());
        if (oldMap != null) {
            synchronized (oldMap) {
                newMap.putAll(oldMap);
            }
        }
        copyOnThreadLocal.set(newMap);
        return newMap;
    }

    @Override
    public void remove(String key) {
        if (key == null) {
            return;
        }
        Map<String, String> oldMap = copyOnThreadLocal.get();
        if (oldMap == null)
            return;
        Integer lastOp = getAndSetLastOperation(WRITE_OPERATION);
        if (wasLastOpReadOrNull(lastOp)) {
            Map<String, String> newMap = duplicateAndInsertNewMap(oldMap);
            newMap.remove(key);
        } else {
            oldMap.remove(key);
        }
    }

    @Override
    public void clear() {
        lastOperation.set(WRITE_OPERATION);
        copyOnThreadLocal.remove();
    }

    @Override
    public String get(String key) {
        final Map<String, String> map = copyOnThreadLocal.get();
        if ((map != null) && (key != null)) {
            return map.get(key);
        } else {
            return null;
        }
    }

    public Set<String> getKeys() {
        Map<String, String> map = getPropertyMap();
        if (map != null) {
            return map.keySet();
        } else {
            return null;
        }
    }
    public Map<String, String> getPropertyMap() {
        lastOperation.set(MAP_COPY_OPERATION);
        return copyOnThreadLocal.get();
    }


    @Override
    public Map<String, String> getCopyOfContextMap() {
        Map<String, String> hashMap = copyOnThreadLocal.get();
        if (hashMap == null) {
            return null;
        } else {
            return new HashMap<String, String>(hashMap);
        }
    }

    @Override
    public void setContextMap(Map<String, String> contextMap) {
        lastOperation.set(WRITE_OPERATION);
        Map<String, String> newMap = Collections.synchronizedMap(new HashMap<String, String>());
        newMap.putAll(contextMap);
        copyOnThreadLocal.set(newMap);
    }

}
