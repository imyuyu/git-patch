package com.github.git.common.ui;

import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * @author imyuyu
 */
public abstract class BaseController implements Initializable {

    public abstract URL getView();

    /**
     * 获取路径
     * @return
     * @throws IOException
     */
    protected final Parent getRoot() throws IOException {
        Parent root = FXMLLoader.load(
                getView()
                ,null
                ,RequestMappingFactory.getInstance().getBuilderFactory()
                ,RequestMappingFactory.getInstance().getControllerFactory());

        return root;
    }

    public abstract Scene getScene() throws IOException;

    /**
     * 当场景展示时做什么事情
     */
    public void onSceneShow(){

    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }
}
