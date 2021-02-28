package com.github.git.common;

import com.github.git.common.ui.MessageDialog;

import java.io.*;
import java.util.Properties;

/**
 * @author imyuyu
 */
public class SystemProperties {

    private final Properties properties = new Properties();
    private final File userPrefs = new File(Environment.getConfigHome(),"git-hotfix.prefs");
    private boolean inited = false;
    private final byte[] lock = new byte[0];
    private static final SystemProperties systemProperties = new SystemProperties();

    public static final String MAVEN_HOME_KEY = "maven_home";
    public static final String GIT_EXECUTABLE_KEY = "git_executable";


    private SystemProperties() {
        init();
    }

    public static SystemProperties getInstance() {
        return systemProperties;
    }

    public void init(){
        if(inited){
            return;
        }

        synchronized (lock){

            if(inited){
                return;
            }

            if (!userPrefs.exists()) {
                try {
                    userPrefs.createNewFile();
                } catch (IOException e) {
                    MessageDialog.alert("创建配置文件失败，请检查相关设置是否正确！");
                    System.exit(0);
                }
            }
            try {
                properties.load(new FileInputStream(userPrefs));
            } catch (IOException e) {
                MessageDialog.alert("读取配置文件失败，请检查相关设置是否正确！");
                System.exit(0);
            }

            inited = true;
        }
    }

    public String getProperty(String name){
        return properties.getProperty(name);
    }

    public void putProperty(String name, String value) {
        properties.put(name,value);
    }

    public void save() throws Exception {

        properties.store(new FileOutputStream(userPrefs,false),"----");
    }

    public void delProperty(String name) {
        properties.remove(name);
    }
}
