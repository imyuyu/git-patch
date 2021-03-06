package com.github.git.patch;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.XmlUtil;
import cn.hutool.core.util.ZipUtil;
import com.github.git.common.Environment;
import com.github.git.common.SystemProperties;
import com.github.git.common.ThreadHelper;
import com.github.git.common.ui.BaseController;
import com.github.git.common.ui.MessageDialog;
import com.github.git.common.ui.RequestMappingFactory;
import com.github.git.config.ConfigController;
import com.github.git.domain.Commit;
import com.github.git.domain.Repository;
import com.github.git.util.GitUtil;
import com.github.git.util.HostServicesHolder;
import com.github.git.util.WindowUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.event.EventTarget;
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
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author imyuyu
 */
public class PatchController extends BaseController implements Initializable {
    private final Logger logger = Logger.getGlobal();

    private final Set<String> exclude = new HashSet<>();

    private final String webapp_key = "src/main/webapp";
    private final String source_key = "src/main/java";

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
     * 设置
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
        exclude.add("pom.xml");
        exclude.add(".gitignore");
        exclude.add(".gitlab-ci.yml");
        exclude.add("CHANGELOG.md");
        exclude.add("CONTRIBUTING.md");
        exclude.add("README.md");
        exclude.add(".mvn");
        exclude.add(".idea");

        String repo = SystemProperties.getInstance().getProperty("repo");
        String pathDir = SystemProperties.getInstance().getProperty("pathDir");

        repoPathInput.setText(repo);
        patchDirInput.setText(pathDir);

        initRepository();
    }

    public void initRepository(){
        String repo = repoPathInput.getText();

        if(StrUtil.isNotBlank(repo)){
            repository = GitUtil.initRepository(repo);

            if(repository != null){
                List<Commit> commits = repository.getCommits();
                if(CollectionUtil.isNotEmpty(commits)){
                    commitEnd.setText(commits.get(0).getAbbreviatedCommitHash());
                    branchInput.setText(repository.getDefaultBranch());
                }
            }
        }
    }

    public void exit(ActionEvent mouseEvent) {
        MessageDialog.confirm("是否确认退出？", new Consumer<ButtonType>() {
            @Override
            public void accept(ButtonType buttonType) {
                System.exit(0);
            }
        });
    }

    public void createPatch(MouseEvent mouseEvent) throws Exception{
        String gitExecutable = SystemProperties.getInstance().getProperty(SystemProperties.GIT_EXECUTABLE_KEY);
        if(StrUtil.isBlank(gitExecutable)){
            MessageDialog.alert("请先配置好Git可执行文件路径！");
            return;
        }

        String workspace = repoPathInput.getText().trim();
        if(StrUtil.isBlank(workspace)){
            MessageDialog.alert("请填写正确的Git本地仓库！");
            repoPathInput.requestFocus();
            return;
        }

        File workspaceFile = new File(workspace);
        if(!workspaceFile.exists()){
            repoPathInput.requestFocus();
            MessageDialog.alert("Git本地仓库不存在！");
            return;
        }

        File gitFile = new File(workspaceFile,".git");
        if(!gitFile.exists()){
            repoPathInput.requestFocus();
            MessageDialog.alert("这不是正确的Git本地仓库！");
            return;
        }

        String commitStartText = commitStart.getText().trim();
        if(StrUtil.isBlank(commitStartText)){
            commitStart.requestFocus();
            MessageDialog.alert("请填写正确的提交hash！");
            return;
        }

        String commitEndText = commitEnd.getText().trim();
        if(StrUtil.isBlank(commitEndText)){
            commitStart.requestFocus();
            MessageDialog.alert("请填写正确的提交hash！");
            return;
        }

        String patchDirText = patchDirInput.getText().trim();
        if(StrUtil.isBlank(patchDirText)){
            patchDirInput.requestFocus();
            MessageDialog.alert("请填写正确的补丁包存放目录！");
            return;
        }

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(
                gitExecutable
                ,"diff"
                ,"--name-only"
                ,commitStartText
                ,commitEndText
                //,">"
                //,Environment.getConfigHome()+File.separator+"git_logs"+File.separator+"logs.txt"
        );
        processBuilder.directory(new File(workspace));
        Map<String, String> environment = processBuilder.environment();
        Process process = processBuilder.start();

        logger.info("开始获取变更详情...");

        List<String> names = new ArrayList<>();

        ThreadHelper.submit(new Runnable() {
            @Override
            public void run() {
                InputStream inputStream = process.getInputStream();
                IoUtil.readUtf8Lines(inputStream,names);
            }
        });

        mainPane.setVisible(false);
        processingPane.setVisible(true);

        progressInfo.clear();
        progressBar.setProgress(0);
        progressInfo.appendText("开始获取变更详情...\n");

        // 是否全量打包
        boolean isFullPackage = fullPackage.isSelected();

        ThreadHelper.submit(new Runnable() {
            @Override
            public void run() {
                int i = 0;
                try {
                    i = process.waitFor();
                } catch (InterruptedException e) {
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setProgress(0);
                            MessageDialog.alert("获取git信息失败");
                            finishPackage.setDisable(false);
                            returnMain.setDisable(false);
                        }
                    });
                    logger.log(Level.SEVERE,"获取git信息失败！",e);
                    return;
                } finally {
                    process.destroyForcibly();
                }

                if(i == 0){

                    if(names.isEmpty()){
                        logger.info("获取变更详情完成，无变更文件");
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                progressInfo.appendText("无变更文件，无需构建补丁包！\n");
                                progressBar.setProgress(1);
                                finishPackage.setDisable(false);
                                returnMain.setDisable(false);
                                MessageDialog.alert("无变更文件，无需构建补丁包！");
                            }
                        });
                        return;
                    }

                    List<String> files = names.stream().filter(PatchController.this::validPath).collect(Collectors.toList());

                    logger.info("一共变更文件"+files.size()+"个,开始进行补丁包制作。");
                    progressInfo.appendText("一共变更文件"+files.size()+"个,开始进行补丁包制作。\n");

                    String commitStartShort = commitStartText.substring(0,8);
                    String commitEndShort = commitEndText.substring(0,8);

                    String patchFileName = String.join("_",commitStartShort,commitEndShort,"patch");

                    File patchDir = new File(Environment.getConfigFolder(),String.join(File.separator,"temp",patchFileName));

                    if(!patchDir.exists()){
                        patchDir.mkdirs();
                    }
                    // 清空文件夹
                    FileUtil.del(patchDir);

                    File classesDir = new File(patchDir,"WEB-INF/classes");
                    if(!classesDir.exists()){
                        classesDir.mkdirs();
                    }

                    long start = System.currentTimeMillis();
                    logger.info("开始构建补丁包..");

                    ThreadHelper.submit(new Runnable() {
                        @Override
                        public void run() {
                            Platform.runLater(new Runnable() {
                                @Override
                                public void run() {
                                    buildInfo.setText("开始构建补丁包...");
                                }
                            });

                            AtomicInteger atomicInteger = new AtomicInteger(0);

                            files.parallelStream().forEach(new Consumer<String>() {
                                @Override
                                public void accept(String filePath) {
                                    Platform.runLater(new Runnable() {
                                        @Override
                                        public void run() {
                                            buildInfo.setText("开始构建补丁包..."+filePath);
                                        }
                                    });

                                    int idx4FileName = filePath.lastIndexOf("/");
                                    // 文件名
                                    String fileName = filePath.substring(idx4FileName + 1);

                                    int idx4ModuleName = filePath.indexOf("/");
                                    // 模块
                                    String moduleName = filePath.substring(0,idx4ModuleName);

                                    // 当前模块打包方式
                                    String packaging = "jar";
                                    // 解析一下pom
                                    File pomFile = new File(workspace+"/"+moduleName+"/pom.xml");
                                    if(pomFile.exists()){
                                        NodeList packagingNodes = XmlUtil.readXML(pomFile).getElementsByTagName("packaging");
                                        if(packagingNodes.getLength() > 0){
                                            packaging = packagingNodes.item(packagingNodes.getLength() - 1).getTextContent();
                                        }
                                    }

                                    int extIndex = fileName.lastIndexOf(".");
                                    String fileShortName = fileName.substring(0, extIndex);
                                    String fileExtName = fileName.substring(extIndex + 1);

                                    String sourcePath = moduleName + "/" + source_key;

                                    File sourceFile;
                                    File targetFile;

                                    if(isFullPackage && Objects.equals(packaging, "jar")){

                                        String jarPath = String.join("/", workspace, moduleName, "target");

                                        File jarFolder = new File(jarPath);
                                        // todo 需要改一下模式，比如从父pom中获取，不过太麻烦了。
                                        File[] jars = jarFolder.listFiles(new FileFilter() {
                                            @Override
                                            public boolean accept(File pathname) {
                                                String fileName = pathname.getName();
                                                return fileName.endsWith(".jar")
                                                        && !fileName.endsWith("SNAPSHOT.jar")
                                                        && !fileName.endsWith("sources.jar");
                                            }
                                        });

                                        if(jars == null || jars.length == 0){
                                            return;
                                        }

                                        sourceFile = jars[0];

                                        targetFile = new File(patchDir+"/WEB-INF/lib/"+sourceFile.getName());

                                        if(targetFile.exists()){
                                            return;
                                        }
                                    }else {
                                        // 需要编译的代码放到classes,不需要编译的直接复制
                                        if (filePath.startsWith(sourcePath)) {
                                            // 需要编译
                                            if (filePath.endsWith(".java")) {
                                                String targetTemp = filePath.replace(sourcePath, "").replace(".java", ".class");

                                                // 需要编译的文件，获取target中的文件
                                                String compilePath = String.join("/", workspace, moduleName, "target", "classes");

                                                String compileFile = filePath.replace(sourcePath, compilePath).replace(".java", ".class");
                                                // class路径
                                                sourceFile = new File(compileFile);
                                                // 补丁包内class路径
                                                targetFile = new File(classesDir, targetTemp);
                                            } else {
                                                // 直接复制
                                                String targetTemp = filePath.replace(sourcePath, "");
                                                sourceFile = new File(workspaceFile, filePath);
                                                targetFile = new File(classesDir, targetTemp);
                                            }
                                        } else {
                                            // 不需要
                                            String targetTemp = filePath.replace(moduleName + "/" + webapp_key, "");
                                            // 其他文件对应放,直接复制
                                            sourceFile = new File(workspaceFile, filePath);
                                            targetFile = new File(patchDir, targetTemp);
                                        }
                                    }

                                    try {
                                        if(sourceFile.exists()){
                                            File parentFile = targetFile.getParentFile();
                                            if(!parentFile.exists()){
                                                parentFile.mkdirs();
                                            }

                                            if(targetFile.exists()){
                                                targetFile.delete();
                                            }
                                            targetFile.createNewFile();

                                            IoUtil.copy(new FileInputStream(sourceFile),new FileOutputStream(targetFile));
                                            logger.info("复制文件："+sourceFile.getPath() + " > " +targetFile.getPath());
                                        }else{
                                            logger.info("源文件不存在,是被删除了:"+sourceFile.getPath());
                                        }

                                        double finalCount = atomicInteger.incrementAndGet();
                                        Platform.runLater(new Runnable() {
                                            @Override
                                            public void run() {
                                                progressInfo.appendText("复制文件："+sourceFile.getPath() + " > " +targetFile.getPath()+". 完成\n");
                                                double percent = finalCount / files.size();
                                                if(percent <= 0.95){
                                                    progressBar.setProgress(percent);
                                                }
                                            }
                                        });
                                    } catch (IOException e) {
                                        logger.log(Level.SEVERE,"失败！",e);
                                    }
                                }
                            });

                            Platform.runLater(new Runnable() {
                                @Override
                                public void run() {
                                    buildInfo.setText("开始进行压缩...");
                                    progressInfo.appendText("...\n");
                                }
                            });
                            ThreadHelper.submit(new Runnable() {
                                @Override
                                public void run() {
                                    // 创建zip文件
                                    File zipFile = new File(patchDirText,patchFileName+".zip");
                                    File parentFile = zipFile.getParentFile();
                                    if(!parentFile.exists()){
                                        parentFile.mkdirs();
                                    }
                                    ZipUtil.zip(zipFile,false,patchDir);

                                    String logInfo = "构建补丁包："+zipFile.getPath()+" 完成,共耗时："+(System.currentTimeMillis()-start)+" MS";
                                    logger.info(logInfo);
                                    Platform.runLater(new Runnable() {
                                        @Override
                                        public void run() {
                                            buildInfo.setText("开始构建补丁包...完成");
                                            progressInfo.appendText(logInfo);

                                            finishPackage.setDisable(false);
                                            returnMain.setDisable(false);
                                            progressBar.setProgress(1);

                                            // 清空文件夹
                                            FileUtil.del(patchDir);

                                            MessageDialog.alert("生成成功！");

                                            if(finishThenOpenFolder.isSelected()){
                                                WindowUtil.openFolder(parentFile.getPath());
                                            }

                                        }
                                    });
                                }
                            });
                        }
                    });

                }else{
                    InputStream inputStream = process.getErrorStream();
                    String s = IoUtil.readUtf8(inputStream);
                    System.out.println(s);
                    logger.severe("生成失败,错误原因："+s);
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            finishPackage.setDisable(false);
                            returnMain.setDisable(false);
                            progressBar.setProgress(1);
                            progressInfo.appendText("生成失败，失败原因：\n"+s);
                            if(s.contains("unknown revision or path not in the working tree.")){
                                MessageDialog.alert("生成失败,提交hash不存在！");
                            }else {
                                MessageDialog.alert("生成失败");
                            }
                        }
                    });
                }
            }
        });

        // 打开文件夹
        if (saveMe.isSelected()) {
            // 保存配置
            SystemProperties.getInstance().putProperty("repo",repoPathInput.getText());
            SystemProperties.getInstance().putProperty("pathDir",patchDirInput.getText());
        }else{
            // 删除配置
            SystemProperties.getInstance().delProperty("repo");
            SystemProperties.getInstance().delProperty("pathDir");
        }
        SystemProperties.getInstance().save();
    }

    public void chooseWorkspace(MouseEvent mouseEvent) {
        String text = repoPathInput.getText();
        if(StrUtil.isNotBlank(text)){
            directoryChooser.setInitialDirectory(new File(text));
        }
        directoryChooser.setTitle("请选择git本地仓库");
        File file = directoryChooser.showDialog(pane.getScene().getWindow());
        if (file != null) {
            repoPathInput.setText(file.getPath());
            // 切换仓库后需要重新加载一次
            initRepository();
        }
    }

    public void choosePatchDir(MouseEvent mouseEvent) {
        String text = patchDirInput.getText();
        if(StrUtil.isNotBlank(text)){
            directoryChooser.setInitialDirectory(new File(text));
        }
        directoryChooser.setTitle("请选择补丁包存放目录");
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
        MessageDialog.alert("拜拜了你嘞！");
        System.exit(0);
    }

    /**
     *
     * @param filePath
     * @return false表示验证失败
     */
    public boolean validPath(String filePath){
        for (String s : exclude) {
            boolean contains = filePath.contains(s) || s.contains(filePath);
            if(contains){
                logger.info("文件被过滤:"+filePath);
                return false;
            }
        }
        return true;
    }

    public void chooseBranch(MouseEvent mouseEvent) {

        String gitExecutable = SystemProperties.getInstance().getProperty(SystemProperties.GIT_EXECUTABLE_KEY);
        if(StrUtil.isBlank(gitExecutable)){
            MessageDialog.alert("请先配置好Git可执行文件路径！");
            return;
        }

        String workspace = repoPathInput.getText().trim();
        if(StrUtil.isBlank(workspace)){
            MessageDialog.alert("请填写正确的Git本地仓库！");
            repoPathInput.requestFocus();
            return;
        }

        File workspaceFile = new File(workspace);
        if(!workspaceFile.exists()){
            repoPathInput.requestFocus();
            MessageDialog.alert("Git本地仓库不存在！");
            return;
        }

        File gitFile = new File(workspaceFile,".git");
        if(!gitFile.exists()){
            repoPathInput.requestFocus();
            MessageDialog.alert("这不是正确的Git本地仓库！");
            return;
        }

        maskPane.setVisible(true);

        // 加载所有分支
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

            logger.info("开始获取分支详情...");

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
                            dialog.setTitle("选择分支");
                            dialog.setHeaderText("");
                            dialog.setContentText("分支:");

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
        chooseCommit(commitStart.getText().trim(),commit -> commitStart.setText(commit.getAbbreviatedCommitHash()));
    }

    public void chooseEndCommit(MouseEvent mouseEvent) {
        chooseCommit(commitEnd.getText().trim(),commit -> commitEnd.setText(commit.getAbbreviatedCommitHash()));
    }

    public void chooseCommit(String initData,Consumer<Commit> consumer){
        Dialog<Commit> dialog = new Dialog<>();
        dialog.setTitle("选择提交历史");
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

        ButtonType okBtn = new ButtonType("确定", ButtonBar.ButtonData.OK_DONE);
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
        dialog.setTitle("关于");
        dialog.setHeaderText("");

        GridPane gridPane = new GridPane();
        Label label = new Label("git补丁包生成工具");
        label.setFont(new Font("宋体",18));
        gridPane.add(label,0,0);

        gridPane.add(new Label(),0,1);

        Label label1 = new Label("作者：imyuyu");
        Label label2 = new Label("联系邮箱：2075904@qq.com");
        gridPane.add(label1,0,2);
        gridPane.add(label2,0,3);

        Label label3 = new Label("当前版本：V1.0");
        gridPane.add(label3,0,4);

        dialog.getDialogPane().setContent(gridPane);

        dialog.getDialogPane().setPrefWidth(300);
        dialog.getDialogPane().setPrefHeight(200);

        dialog.getDialogPane().getButtonTypes().add(new ButtonType("关闭", ButtonBar.ButtonData.CANCEL_CLOSE));

        dialog.show();
    }
}
