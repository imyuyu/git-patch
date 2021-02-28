package com.github.git.common.ui;

import com.github.git.config.ConfigController;
import com.github.git.patch.PatchController;
import javafx.fxml.JavaFXBuilderFactory;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.BuilderFactory;
import javafx.util.Callback;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * @author imyuyu
 */
public class RequestMappingFactory {

    private static final RequestMappingFactory requestMappingFactory = new RequestMappingFactory();
    private final Map<Class<?>,Scene> classSceneMap = new ConcurrentHashMap<>();
    private final Map<Class<?>,ControllerWrapper> baseControllerMap = new ConcurrentHashMap<>();
    private final BuilderFactory builderFactory = new JavaFXBuilderFactory();

    public static RequestMappingFactory getInstance(){
        return requestMappingFactory;
    }

    private Stage stage;

    /**
     * 主舞台
     * @return
     */
    public Stage getStage() {
        return stage;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    private ControllerWrapper getControllerWrapper(Class<?> controllerClass) {
        return baseControllerMap.computeIfAbsent(controllerClass, new Function<Class<?>, ControllerWrapper>() {
            @Override
            public ControllerWrapper apply(Class<?> aClass) {
                try {
                    Object baseController = aClass.newInstance();
                    return new ControllerWrapper(controllerClass,baseController);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return ControllerWrapper.EMPTY;
            }
        });
    }

    public <T> T getController(Class<T> controllerClass){
        ControllerWrapper controllerWrapper = getControllerWrapper(controllerClass);
        return controllerWrapper.getController();
    }

    public Scene getScene(Class<?> controllerClass) {
        return classSceneMap.computeIfAbsent(controllerClass, new Function<Class<?>, Scene>() {
            @Override
            public Scene apply(Class<?> aClass) {
                Object controller = getController(aClass);
                if(controller instanceof BaseController){
                    try {
                        return ((BaseController) controller).getScene();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return null;
            }
        });
    }

    public void showScene(Class<?> controllerClass) {
        Scene scene = getScene(controllerClass);
        if(scene != null){
            ControllerWrapper controllerWrapper = getControllerWrapper(controllerClass);
            if(controllerWrapper.isInit()){
                // 已经初始化了才调用
                Object controller = getController(controllerClass);
                if (controller instanceof BaseController){
                    ((BaseController) controller).onSceneShow();
                }
            }else{
                controllerWrapper.setInit();
            }
            getStage().setScene(scene);
        }else{
            throw new RuntimeException("场景不存在！");
        }
    }

    public BuilderFactory getBuilderFactory() {
        return builderFactory;
    }

    public Callback<Class<?>, Object> getControllerFactory() {
        return this::getController;
    }


    static class ControllerWrapper{
        public static final ControllerWrapper EMPTY = new ControllerWrapper(null, null);

        private Class<?> clazz;
        private Object controller;
        private boolean init = false;

        public ControllerWrapper(Class<?> clazz, Object controller) {
            this.clazz = clazz;
            this.controller = controller;
        }

        public Class<?> getClazz() {
            return clazz;
        }

        public <T> T getController() {
            return (T) controller;
        }

        public boolean isInit(){
            return init;
        }

        public void setInit(){
            this.init = true;
        }
    }
}
