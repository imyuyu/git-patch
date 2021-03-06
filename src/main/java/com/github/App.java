package com.github;


import com.github.git.util.MessageDialog;
import com.github.git.util.ui.RequestMappingFactory;
import com.github.git.patch.PatchController;
import com.github.git.util.HostServicesHolder;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.logging.LogManager;

public class App extends Application
{

    static{
        // 初始化日志文件
        try {
            LogManager.getLogManager().readConfiguration(App.class.getResourceAsStream("/logging.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception{

        HostServicesHolder.setHostServices(getHostServices());

        RequestMappingFactory.getInstance().setStage(primaryStage);
        Scene scene = RequestMappingFactory.getInstance().getScene(PatchController.class);
        primaryStage.setTitle("补丁包生成工具V1.0");
        primaryStage.setResizable(false);
        primaryStage.setScene(scene);
        //primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.show();

        primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent event) {
                event.consume();
                MessageDialog.confirm("是否确认退出？", new Consumer<ButtonType>() {
                    @Override
                    public void accept(ButtonType buttonType) {
                        System.exit(0);
                    }
                });
            }
        });

        Platform.setImplicitExit(false);

    }

    public static void main( String[] args )
    {
        launch(args);
    }
}
