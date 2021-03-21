package com.github.git.config;

import cn.hutool.core.util.StrUtil;
import com.github.git.common.Constants;
import com.github.git.common.SystemProperties;
import com.github.git.util.UIHelper;
import com.github.git.util.ui.BaseController;
import com.github.git.util.MessageDialog;
import com.github.git.util.ui.RequestMappingFactory;
import com.github.git.patch.PatchController;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * @author imyuyu
 */
public class ConfigController extends BaseController implements Initializable {
    @FXML
    private Pane pane;
    @FXML
    public TextField mavenHomeInput;
    @FXML
    public Button cancelBtn;
    @FXML
    public Button chooseMavenHome;
    @FXML
    public TextField gitExecutableInput;
    @FXML
    private Button saveBtn;

    public TextField sourceFolder;
    public TextField resourceFolder;
    public TextField webappFolder;
    public ListView<String> excludedListView;

    private DirectoryChooser directoryChooser = new DirectoryChooser();
    private FileChooser fileChooser = new FileChooser();

    @Override
    public URL getView() {
        return getClass().getResource("/views/config.fxml");
    }

    @Override
    public Scene getScene() throws IOException {
        return new Scene(getRoot(), 600, 400);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        init();
    }

    @Override
    public void onSceneShow() {
        init();
    }

    public void init(){
        // 读取配置文件
        String mavenHome = SystemProperties.getInstance().getProperty(SystemProperties.MAVEN_HOME_KEY);
        if(StrUtil.isBlank(mavenHome)){
            mavenHome = System.getenv(Constants.ENV_MAVEN_HOME_KEY);
        }

        mavenHomeInput.setText(mavenHome);

        String gitExecutable = SystemProperties.getInstance().getProperty(SystemProperties.GIT_EXECUTABLE_KEY);

        gitExecutableInput.setText(gitExecutable);

        directoryChooser.setTitle("选择Maven目录");

        fileChooser.setTitle("请选择git可执行文件路径");

        sourceFolder.setText(SystemProperties.getInstance().getSourceFolder());
        resourceFolder.setText(SystemProperties.getInstance().getResourceFolder());
        webappFolder.setText(SystemProperties.getInstance().getWebappFolder());

        ObservableList<String> excludedList = FXCollections.observableArrayList();
        excludedList.addAll(SystemProperties.getInstance().getExcludedFile());
        excludedListView.setItems(excludedList);
    }

    public void saveConfig(MouseEvent mouseEvent){
        SystemProperties.getInstance().putProperty(SystemProperties.MAVEN_HOME_KEY, UIHelper.val(mavenHomeInput));
        SystemProperties.getInstance().putProperty(SystemProperties.GIT_EXECUTABLE_KEY, UIHelper.val(gitExecutableInput));
        try {
            SystemProperties.getInstance().save();
            MessageDialog.alert("保存成功！");
        } catch (Exception e) {
            MessageDialog.error("保存失败！");
        }
    }

    public void chooseMavenHome(MouseEvent mouseEvent){
        String text = UIHelper.val(mavenHomeInput);
        if(StrUtil.isNotBlank(text)){
            directoryChooser.setInitialDirectory(new File(text));
        }
        File file = directoryChooser.showDialog(pane.getScene().getWindow());
        if (file == null) {
            return;
        }
        mavenHomeInput.setText(file.getPath());
    }

    public void chooseGitExecutable(MouseEvent mouseEvent){
        String text = UIHelper.val(gitExecutableInput);
        if(StrUtil.isNotBlank(text)){
            File file = new File(text);
            if(file.exists()){
                fileChooser.setInitialDirectory(file.getParentFile());
                fileChooser.setInitialFileName(file.getName());
            }
        }
         File file = fileChooser.showOpenDialog(pane.getScene().getWindow());
        if (file == null) {
            return;
        }
        gitExecutableInput.setText(file.getPath());
    }

    public void returnHome(){
        RequestMappingFactory.getInstance().showScene(PatchController.class);
    }

    public void addPattern(MouseEvent mouseEvent) {
        String prompt = MessageDialog.prompt(null, null);
        if(StrUtil.isNotBlank(prompt)){
            excludedListView.getItems().add(prompt);
        }
    }

    public void editPattern(MouseEvent mouseEvent) {
        String selectedItem = excludedListView.getSelectionModel().getSelectedItem();
        if(selectedItem != null){
            String prompt = MessageDialog.prompt(null, selectedItem);
            excludedListView.getItems().set(excludedListView.getSelectionModel().getSelectedIndex(),prompt);
        }

    }

    public void delPattern(MouseEvent mouseEvent) {
        ObservableList<String> selectedItems = excludedListView.getSelectionModel().getSelectedItems();
        excludedListView.getItems().removeAll(selectedItems);
    }
}
