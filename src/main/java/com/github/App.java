package com.github;


import com.github.git.common.Environment;
import com.github.git.common.ui.MessageDialog;
import com.github.git.common.ui.RequestMappingFactory;
import com.github.git.patch.PatchController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

import java.io.FileInputStream;
import java.util.function.Consumer;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class App extends Application
{

    static{
        String path = App.class.getClassLoader()
                .getResource("logging.properties")
                .getFile();
        System.setProperty("java.util.logging.config.file", path);

        // 初始化日志文件
        // LogManager.getLogManager().readConfiguration(App.class.getResourceAsStream("/logging.properties"));
    }

    @Override
    public void start(Stage primaryStage) throws Exception{

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
