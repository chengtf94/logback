package ch.qos.logback.core;

import static ch.qos.logback.core.CoreConstants.CODES_URL;
import static ch.qos.logback.core.CoreConstants.MORE_INFO_PREFIX;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.Map;
import java.util.Map.Entry;

import ch.qos.logback.core.recovery.ResilientFileOutputStream;
import ch.qos.logback.core.util.ContextUtil;
import ch.qos.logback.core.util.FileSize;
import ch.qos.logback.core.util.FileUtil;

/**
 * 文件追加器
 * 
 * @author Ceki G&uuml;lc&uuml;
 */
public class FileAppender<E> extends OutputStreamAppender<E> {

    /** 文件名称、默认缓冲区大小 */
    static protected String COLLISION_WITH_EARLIER_APPENDER_URL = CODES_URL + "#earlier_fa_collision";
    protected boolean append = true;
    protected String fileName = null;
    private boolean prudent = false;
    public static final long DEFAULT_BUFFER_SIZE = 8192;
    private FileSize bufferSize = new FileSize(DEFAULT_BUFFER_SIZE);

    @Override
    public void start() {
        int errors = 0;
        if (getFile() != null) {
            addInfo("File property is set to [" + fileName + "]");
            if (prudent) {
                if (!isAppend()) {
                    setAppend(true);
                    addWarn("Setting \"Append\" property to true on account of \"Prudent\" mode");
                }
            }
            if (checkForFileCollisionInPreviousFileAppenders()) {
                addError("Collisions detected with FileAppender/RollingAppender instances defined earlier. Aborting.");
                addError(MORE_INFO_PREFIX + COLLISION_WITH_EARLIER_APPENDER_URL);
                errors++;
            } else {
                try {
                    // 打开文件
                    openFile(getFile());
                } catch (java.io.IOException e) {
                    errors++;
                    addError("openFile(" + fileName + "," + append + ") call failed.", e);
                }
            }
        } else {
            errors++;
            addError("\"File\" property not set for appender named [" + name + "].");
        }
        if (errors == 0) {
            super.start();
        }
    }
    /**
     * 打开文件
     */
    public void openFile(String file_name) throws IOException {
        lock.lock();
        try {
            File file = new File(file_name);
            boolean result = FileUtil.createMissingParentDirectories(file);
            if (!result) {
                addError("Failed to create parent directories for [" + file.getAbsolutePath() + "]");
            }

            ResilientFileOutputStream resilientFos = new ResilientFileOutputStream(file, append, bufferSize.getSize());
            resilientFos.setContext(context);
            setOutputStream(resilientFos);
        } finally {
            lock.unlock();
        }
    }


    public void setFile(String file) {
        if (file == null) {
            fileName = file;
        } else {
            fileName = file.trim();
        }
    }

    public boolean isAppend() {
        return append;
    }
    final public String rawFileProperty() {
        return fileName;
    }
    public String getFile() {
        return fileName;
    }

    @Override
    public void stop() {
        super.stop();

        Map<String, String> map = ContextUtil.getFilenameCollisionMap(context);
        if (map == null || getName() == null)
            return;

        map.remove(getName());
    }

    protected boolean checkForFileCollisionInPreviousFileAppenders() {
        boolean collisionsDetected = false;
        if (fileName == null) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, String> map = (Map<String, String>) context.getObject(CoreConstants.FA_FILENAME_COLLISION_MAP);
        if (map == null) {
            return collisionsDetected;
        }
        for (Entry<String, String> entry : map.entrySet()) {
            if (fileName.equals(entry.getValue())) {
                addErrorForCollision("File", entry.getValue(), entry.getKey());
                collisionsDetected = true;
            }
        }
        if (name != null) {
            map.put(getName(), fileName);
        }
        return collisionsDetected;
    }

    protected void addErrorForCollision(String optionName, String optionValue, String appenderName) {
        addError("'" + optionName + "' option has the same value \"" + optionValue + "\" as that given for appender [" + appenderName + "] defined earlier.");
    }



    /**
     * @see #setPrudent(boolean)
     * 
     * @return true if in prudent mode
     */
    public boolean isPrudent() {
        return prudent;
    }

    /**
     * When prudent is set to true, file appenders from multiple JVMs can safely
     * write to the same file.
     * 
     * @param prudent
     */
    public void setPrudent(boolean prudent) {
        this.prudent = prudent;
    }

    public void setAppend(boolean append) {
        this.append = append;
    }
    
    public void setBufferSize(FileSize bufferSize) {
        addInfo("Setting bufferSize to ["+bufferSize.toString()+"]");
        this.bufferSize = bufferSize;
    }

    private void safeWrite(E event) throws IOException {
        ResilientFileOutputStream resilientFOS = (ResilientFileOutputStream) getOutputStream();
        FileChannel fileChannel = resilientFOS.getChannel();
        if (fileChannel == null) {
            return;
        }

        // Clear any current interrupt (see LOGBACK-875)
        boolean interrupted = Thread.interrupted();

        FileLock fileLock = null;
        try {
            fileLock = fileChannel.lock();
            long position = fileChannel.position();
            long size = fileChannel.size();
            if (size != position) {
                fileChannel.position(size);
            }
            super.writeOut(event);
        } catch (IOException e) {
            // Mainly to catch FileLockInterruptionExceptions (see LOGBACK-875)
            resilientFOS.postIOFailure(e);
        } finally {
            if (fileLock != null && fileLock.isValid()) {
                fileLock.release();
            }

            // Re-interrupt if we started in an interrupted state (see LOGBACK-875)
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    protected void writeOut(E event) throws IOException {
        if (prudent) {
            safeWrite(event);
        } else {
            super.writeOut(event);
        }
    }
}
