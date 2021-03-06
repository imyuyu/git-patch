package com.github.git.util;

import javafx.application.HostServices;

/**
 * @author imyuyu
 */
public class HostServicesHolder {

    private static HostServices hostServices;

    public static HostServices getHostServices(){
        return hostServices;
    }

    public static void setHostServices(HostServices hostServices){
        HostServicesHolder.hostServices = hostServices;
    }

}
