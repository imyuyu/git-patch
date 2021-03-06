package com.github.git.util;

import com.github.git.common.Environment;

import java.nio.file.Paths;

/**
 * @author imyuyu
 */
public class WindowUtil {

    public static void openFolder(String path){
        HostServicesHolder.getHostServices().showDocument(Paths.get(path).toUri().toString());
    }

}
