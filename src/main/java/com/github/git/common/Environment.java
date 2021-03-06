package com.github.git.common;

import java.io.File;

/**
 * @author imyuyu
 */
public class Environment {

    private static final File userHome = new File(System.getProperty("user.home"));

    private static final File configHome = new File(userHome,".git-hotfix");

    static {
        if (!configHome.exists()) {
            configHome.mkdirs();
        }
    }

    public static File getUserHome(){
        return userHome;
    }

    public static File getConfigFolder(){
        return configHome;
    }

    public static File getLogFolder(){
        return new File(configHome,"logs");
    }

    public static String getGitExecutable(){
        return SystemProperties.getInstance().getProperty(SystemProperties.GIT_EXECUTABLE_KEY);
    }

}
