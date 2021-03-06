package com.github.jul.logger;

import com.github.git.common.Environment;

import java.io.IOException;
import java.util.logging.FileHandler;

/**
 * @author imyuyu
 */
public class JulFileHandler extends FileHandler {

    public JulFileHandler() throws IOException, SecurityException {
        this(Environment.getLogFolder().getPath()+"/java%u.log",false);
    }

    public JulFileHandler(String pattern) throws IOException, SecurityException {
        super(pattern);
    }

    public JulFileHandler(String pattern, boolean append) throws IOException, SecurityException {
        super(pattern, append);
    }

    public JulFileHandler(String pattern, int limit, int count) throws IOException, SecurityException {
        super(pattern, limit, count);
    }

    public JulFileHandler(String pattern, int limit, int count, boolean append) throws IOException, SecurityException {
        super(pattern, limit, count, append);
    }
}
