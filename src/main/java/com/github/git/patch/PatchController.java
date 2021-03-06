package com.github.git.patch;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.XmlUtil;
import cn.hutool.core.util.ZipUtil;
import com.github.git.common.Environment;
import com.github.git.common.SystemProperties;
import com.github.git.patch.task.FetchPatchInfoTask;
import com.github.git.patch.task.PatchBuildTask;
import com.github.git.util.ThreadHelper;
import com.github.git.util.UIHelper;
import com.github.git.util.ui.BaseController;
import com.github.git.util.MessageDialog;
import com.github.git.util.ui.RequestMappingFactory;
import com.github.git.config.ConfigController;
import com.github.git.domain.Commit;
import com.github.git.domain.Repository;
import com.github.git.util.GitUtil;
import com.github.git.util.WindowUtil;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.util.Callback;
import org.w3c.dom.NodeList;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author imyuyu
 */
public class PatchController extends BaseController implements Initializable {
    private final Logger logger = Logger.getGlobal();

    @FXML
    public Pane pane;
    @FXML
    public TextField repoPathInput;
    @FXML
    public TextField patchDirInput;
    @FXML
    public TextField commitStart;
    @FXML
    public TextField commitEnd;
    @FXML
    public CheckBox saveMe;
    public AnchorPane mainPane;
    public AnchorPane processingPane;
    public ProgressBar progressBar;
    public TextArea progressInfo;
    public Button finishPackage;
    public Button returnMain;
    public Label buildInfo;
    public StackPane maskPane;
    public TextField branchInput;
    public CheckBox finishThenOpenFolder;
    public CheckBox fullPackage;

    private DirectoryChooser directoryChooser = new DirectoryChooser();

    private Repository repository;

    /**
     * ??????
     * @param mouseEvent
     */
    public void setting(ActionEvent mouseEvent) {
        RequestMappingFactory.getInstance().showScene(ConfigController.class);
    }

    @Override
    public URL getView() {
        return getClass().getResource("/views/patch.fxml");
    }

    @Override
    public Scene getScene() throws IOException {
        return new Scene(getRoot(), 600, 400);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        String repo = SystemProperties.getInstance().getProperty("repo");
        String pathDir = SystemProperties.getInstance().getProperty("pathDir");

        repoPathInput.setText(repo);
        patchDirInput.setText(pathDir);

        initRepository();
    }

    public void initRepository(){
        // ?????????
        repository = null;

        String repo = UIHelper.val(repoPathInput);

        if(StrUtil.isNotBlank(repo)){

            File file = new File(repo, ".git");
            if(!file.exists()){
                MessageDialog.error("??????????????????????????????");
                return;
            }

            Task<Void> task = new Task<Void>() {

                @Override
                protected Void call() throws Exception {
                    try {
                        repository = GitUtil.initRepository(repo);
                    } catch (Exception e) {
                        logger.log(Level.SEVERE,"?????????????????????: "+e.getLocalizedMessage(),e);
                        MessageDialog.error("????????????????????????"+e.getLocalizedMessage());
                    }

                    List<Commit> commits = repository.getCommits();
                    if(CollectionUtil.isNotEmpty(commits)){
                        commitEnd.setText(commits.get(0).getAbbreviatedCommitHash());
                        branchInput.setText(repository.getDefaultBranch());
                    }

                    return null;
                }
            };

            maskPane.visibleProperty().bind(task.runningProperty());

            ThreadHelper.submit(task);
        }
    }

    private void showProgress() {
        maskPane.setVisible(true);
    }

    public void exit(ActionEvent mouseEvent) {
        MessageDialog.confirm("?????????????????????", new Consumer<ButtonType>() {
            @Override
            public void accept(ButtonType buttonType) {
                System.exit(0);
            }
        });
    }

    public void createPatch(MouseEvent mouseEvent) throws Exception{
        String gitExecutable = SystemProperties.getInstance().getProperty(SystemProperties.GIT_EXECUTABLE_KEY);
        if(StrUtil.isBlank(gitExecutable)){
            MessageDialog.alert("???????????????Git????????????????????????");
            return;
        }

        String workspace = UIHelper.val(repoPathInput);
        if(StrUtil.isBlank(workspace)){
            MessageDialog.alert("??????????????????Git???????????????");
            repoPathInput.requestFocus();
            return;
        }

        File workspaceFile = new File(workspace);
        if(!workspaceFile.exists()){
            repoPathInput.requestFocus();
            MessageDialog.alert("Git????????????????????????");
            return;
        }

        File gitFile = new File(workspaceFile,".git");
        if(!gitFile.exists()){
            repoPathInput.requestFocus();
            MessageDialog.alert("??????????????????Git???????????????");
            return;
        }

        if (!isValid()) {
            MessageDialog.alert("??????????????????Git???????????????");
            return;
        }

        String commitStartText = UIHelper.val(commitStart);
        if(StrUtil.isBlank(commitStartText)){
            commitStart.requestFocus();
            MessageDialog.alert("????????????????????????hash???");
            return;
        }

        String commitEndText = UIHelper.val(commitEnd);
        if(StrUtil.isBlank(commitEndText)){
            commitStart.requestFocus();
            MessageDialog.alert("????????????????????????hash???");
            return;
        }

        String patchDirText = UIHelper.val(patchDirInput);
        if(StrUtil.isBlank(patchDirText)){
            patchDirInput.requestFocus();
            MessageDialog.alert("??????????????????????????????????????????");
            return;
        }

        FetchPatchInfoTask fetchPatchInfoTask = new FetchPatchInfoTask(gitExecutable,commitStartText,commitEndText,workspaceFile);
        fetchPatchInfoTask.setOnRunning(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                progressInfo.clear();
                progressInfo.appendText("????????????????????????...\n");
            }
        });

        fetchPatchInfoTask.exceptionProperty().addListener(new ChangeListener<Throwable>() {
            @Override
            public void changed(ObservableValue<? extends Throwable> observable, Throwable oldValue, Throwable e) {
                MessageDialog.alert(e.getLocalizedMessage());
            }
        });

        fetchPatchInfoTask.valueProperty().addListener(new ChangeListener<List<String>>() {
            @Override
            public void changed(ObservableValue<? extends List<String>> observable, List<String> oldValue, List<String> names) {
                if (names.isEmpty()) {
                    MessageDialog.alert("??????????????????????????????????????????");
                    return;
                }
                startBuildPatch(names,commitStartText,commitEndText,workspace,patchDirText);
            }
        });

        maskPane.visibleProperty().bind(fetchPatchInfoTask.runningProperty());

        ThreadHelper.submit(fetchPatchInfoTask);

        // ???????????????
        if (saveMe.isSelected()) {
            // ????????????
            SystemProperties.getInstance().putProperty("repo", UIHelper.val(repoPathInput));
            SystemProperties.getInstance().putProperty("pathDir", UIHelper.val(patchDirInput));
        }else{
            // ????????????
            SystemProperties.getInstance().delProperty("repo");
            SystemProperties.getInstance().delProperty("pathDir");
        }
        SystemProperties.getInstance().save();
    }

    /**
     * ?????????????????????
     * @param names
     * @param commitStartText
     * @param commitEndText
     * @param workspace
     * @param saveFolder
     */
    private void startBuildPatch(List<String> names, String commitStartText, String commitEndText,String workspace,String saveFolder){

        PatchBuildTask patchBuildTask = new PatchBuildTask(names,commitStartText,commitEndText,workspace,saveFolder);
        // ??????????????????
        patchBuildTask.setFullPackage(fullPackage.isSelected());

        SimpleBooleanProperty trueProperty = new SimpleBooleanProperty(true);

        patchBuildTask.setOnRunning(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                mainPane.setVisible(false);
                processingPane.setVisible(true);

                progressInfo.clear();
            }
        });

        patchBuildTask.messageProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                progressInfo.appendText(newValue);
                progressInfo.appendText("\n");
            }
        });

        buildInfo.textProperty().bind(patchBuildTask.titleProperty());


        patchBuildTask.valueProperty().addListener(new ChangeListener<File>() {
            @Override
            public void changed(ObservableValue<? extends File> observable, File oldValue, File newValue) {
                MessageDialog.alert("???????????????");

                if (finishThenOpenFolder.isSelected() && newValue != null) {
                    WindowUtil.openFolder(newValue.getParentFile().getPath());
                }
            }
        });

        patchBuildTask.exceptionProperty().addListener(new ChangeListener<Throwable>() {
            @Override
            public void changed(ObservableValue<? extends Throwable> observable, Throwable oldValue, Throwable newValue) {
                logger.log(Level.SEVERE,"???????????????",newValue);
                MessageDialog.alert("???????????????");
            }
        });

        progressBar.progressProperty().bind(patchBuildTask.progressProperty());

        BooleanBinding isOk = patchBuildTask.runningProperty().isEqualTo(trueProperty);

        finishPackage.disableProperty().bind(isOk);
        returnMain.disableProperty().bind(isOk);

        ThreadHelper.submit(patchBuildTask);
    }

    public void chooseWorkspace(MouseEvent mouseEvent) {
        String text = UIHelper.val(repoPathInput);
        if(StrUtil.isNotBlank(text)){
            directoryChooser.setInitialDirectory(new File(text));
        }
        directoryChooser.setTitle("?????????git????????????");
        File file = directoryChooser.showDialog(pane.getScene().getWindow());
        if (file != null) {
            repoPathInput.setText(file.getPath());
            // ???????????????????????????????????????
            initRepository();
        }
    }

    public void choosePatchDir(MouseEvent mouseEvent) {
        String text = UIHelper.val(patchDirInput);
        if(StrUtil.isNotBlank(text)){
            directoryChooser.setInitialDirectory(new File(text));
        }
        directoryChooser.setTitle("??????????????????????????????");
        File file = directoryChooser.showDialog(pane.getScene().getWindow());
        if (file != null) {
            patchDirInput.setText(file.getPath());
        }
    }

    public void returnMain(MouseEvent mouseEvent) {
        mainPane.setVisible(true);
        processingPane.setVisible(false);
    }

    public void finishPackage(MouseEvent mouseEvent) {
        MessageDialog.alert("??????????????????");
        System.exit(0);
    }

    /**
     * ??????????????????
     * @return
     */
    public boolean isValid(){
        return repository != null;
    }

    public void chooseBranch(MouseEvent mouseEvent) {

        String gitExecutable = SystemProperties.getInstance().getProperty(SystemProperties.GIT_EXECUTABLE_KEY);
        if(StrUtil.isBlank(gitExecutable)){
            MessageDialog.alert("???????????????Git????????????????????????");
            return;
        }

        String workspace = UIHelper.val(repoPathInput);
        if(StrUtil.isBlank(workspace)){
            MessageDialog.alert("??????????????????Git???????????????");
            repoPathInput.requestFocus();
            return;
        }

        File workspaceFile = new File(workspace);
        if(!workspaceFile.exists()){
            repoPathInput.requestFocus();
            MessageDialog.alert("Git????????????????????????");
            return;
        }

        File gitFile = new File(workspaceFile,".git");
        if(!gitFile.exists()){
            repoPathInput.requestFocus();
            MessageDialog.alert("??????????????????Git???????????????");
            return;
        }

        maskPane.setVisible(true);

        // ??????????????????
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(
                gitExecutable
                ,"branch"
                ,"-a"
        );
        processBuilder.directory(new File(workspace));
        Map<String, String> environment = processBuilder.environment();

        List<String> names = new ArrayList<>();

        try {
            Process process = processBuilder.start();

            logger.info("????????????????????????...");

            ThreadHelper.submit(new Runnable() {
                @Override
                public void run() {
                    InputStream inputStream = process.getInputStream();
                    IoUtil.readUtf8Lines(inputStream,names);
                }
            });

            ThreadHelper.submit(new Runnable() {
                @Override
                public void run() {
                    int i = 0;
                    try {
                        i = process.waitFor();
                    }catch (Exception e){

                    }

                    maskPane.setVisible(false);

                    if(i != 0){
                        return;
                    }

                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {

                            List<String> dealNames = names.stream().map(new Function<String, String>() {
                                @Override
                                public String apply(String s) {
                                    String name = s.trim();
                                    if(name.startsWith("*")){
                                        name = name.replace("*","").trim();
                                    }else if(name.contains("->")){

                                        String[] nameInfo = name.split("->");
                                        name = nameInfo[1].trim();
                                    }
                                    return name;
                                }
                            }).collect(Collectors.toList());

                            String defaultBranch = dealNames.stream().filter(s -> s.startsWith("*")).findFirst().orElse(dealNames.get(0));

                            ChoiceDialog<String> dialog = new ChoiceDialog<>(defaultBranch,dealNames);
                            dialog.setTitle("????????????");
                            dialog.setHeaderText("");
                            dialog.setContentText("??????:");

                            Optional<String> result = dialog.showAndWait();
                            result.ifPresent(s -> branchInput.setText(s));
                        }
                    });

                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void chooseStartCommit(MouseEvent mouseEvent) {
        chooseCommit(UIHelper.val(commitStart), commit -> commitStart.setText(commit.getAbbreviatedCommitHash()));
    }

    public void chooseEndCommit(MouseEvent mouseEvent) {

        chooseCommit(UIHelper.val(commitEnd), commit -> commitEnd.setText(commit.getAbbreviatedCommitHash()));
    }

    public void chooseCommit(String initData,Consumer<Commit> consumer){
        if(!isValid()){
            MessageDialog.alert("??????????????????????????????");
            return;
        }

        Dialog<Commit> dialog = new Dialog<>();
        dialog.setTitle("??????????????????");
        dialog.setHeaderText("");

        ObservableList<Commit> data = FXCollections.observableArrayList();
        data.addAll(repository.getCommits());

        GridPane gridPane = new GridPane();

        TableView<Commit> tableView = new TableView<>(data);
        tableView.setPrefWidth(800);
        tableView.setPrefHeight(600);
        tableView.getColumns().addAll(CommitTableHelper.getTableColumns());

        if(StrUtil.isNotBlank(initData)){
            data.parallelStream()
                    .filter(commit -> Objects.equals(commit.getAbbreviatedCommitHash(), initData))
                    .findFirst()
                    .ifPresent(commit -> {
                        int row = data.indexOf(commit);
                        tableView.getSelectionModel().select(row);
                        tableView.getSelectionModel().focus(row);
                        tableView.scrollTo(row);
                    });
        }

        tableView.setRowFactory( tv -> {
            TableRow<Commit> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && (!row.isEmpty()) ) {
                    Commit rowData = row.getItem();
                    dialog.setResult(rowData);
                    dialog.close();
                }
            });
            return row ;
        });

        Platform.runLater(tableView::requestFocus);

        gridPane.add(tableView,0,0);
        /*Pagination pagination = new Pagination(10,0);
        gridPane.add(pagination,0,1);*/

        dialog.getDialogPane().setContent(gridPane);

        dialog.getDialogPane().setPrefWidth(800);
        dialog.getDialogPane().setPrefHeight(600);

        ButtonType okBtn = new ButtonType("??????", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okBtn, ButtonType.CANCEL);

        dialog.setResultConverter(new Callback<ButtonType, Commit>() {
            @Override
            public Commit call(ButtonType param) {
                if (param.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                    return tableView.getSelectionModel().getSelectedItem();
                }
                return null;
            }
        });

        Optional<Commit> o = dialog.showAndWait();

        o.ifPresent(consumer);
    }

    public void showLogFolder(ActionEvent actionEvent) {
        WindowUtil.openFolder(Environment.getLogFolder().getPath());
    }

    public void about(ActionEvent actionEvent) {
        Dialog<Commit> dialog = new Dialog<>();
        dialog.setTitle("??????");
        dialog.setHeaderText("");

        GridPane gridPane = new GridPane();
        Label label = new Label("git?????????????????????");
        label.setFont(new Font("??????",18));
        gridPane.add(label,0,0);

        gridPane.add(new Label(),0,1);

        Label label1 = new Label("?????????imyuyu");
        Label label2 = new Label("???????????????2075904@qq.com");
        gridPane.add(label1,0,2);
        gridPane.add(label2,0,3);

        Label label3 = new Label("???????????????V1.0");
        gridPane.add(label3,0,4);

        dialog.getDialogPane().setContent(gridPane);

        dialog.getDialogPane().setPrefWidth(300);
        dialog.getDialogPane().setPrefHeight(200);

        dialog.getDialogPane().getButtonTypes().add(new ButtonType("??????", ButtonBar.ButtonData.CANCEL_CLOSE));

        dialog.show();
    }
}
