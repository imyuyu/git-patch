package com.github.git.patch;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import com.github.git.common.Environment;
import com.github.git.common.SystemProperties;
import com.github.git.common.ThreadHelper;
import com.github.git.common.ui.BaseController;
import com.github.git.common.ui.MessageDialog;
import com.github.git.common.ui.RequestMappingFactory;
import com.github.git.config.ConfigController;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
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

    private DirectoryChooser directoryChooser = new DirectoryChooser();

    /**
     * 设置
     * @param mouseEvent
     */
    public void setting(MouseEvent mouseEvent) {
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
    }

    public void exit(MouseEvent mouseEvent) {
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
                            MessageDialog.alert("获取git信息失败");
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

                    File patchDir = new File(Environment.getConfigHome(),String.join(File.separator,"temp",patchFileName));

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

                                    int extIndex = fileName.lastIndexOf(".");
                                    String fileShortName = fileName.substring(0, extIndex);
                                    String fileExtName = fileName.substring(extIndex + 1);

                                    String sourcePath = moduleName + "/" + source_key;

                                    File sourceFile;
                                    File targetFile;

                                    if(filePath.startsWith(sourcePath)){
                                        // 需要编译
                                        if(filePath.endsWith(".java")){
                                            String targetTemp = filePath.replace(sourcePath,"").replace(".java", ".class");

                                            // 需要编译的文件，获取target中的文件
                                            String compilePath = String.join("/",workspace,moduleName,"target","classes");

                                            String compileFile = filePath.replace(sourcePath, compilePath).replace(".java", ".class");
                                            // class路径
                                            sourceFile = new File(compileFile);
                                            // 补丁包内class路径
                                            targetFile = new File(classesDir,targetTemp);
                                        }else{
                                            // 直接复制
                                            String targetTemp = filePath.replace(sourcePath,"");
                                            sourceFile = new File(workspaceFile,filePath);
                                            targetFile = new File(classesDir,targetTemp);
                                        }
                                    }else{
                                        // 不需要
                                        String targetTemp = filePath.replace(moduleName+"/"+webapp_key,"");
                                        // 其他文件对应放,直接复制
                                        sourceFile = new File(workspaceFile,filePath);
                                        targetFile = new File(patchDir,targetTemp);
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

                                            MessageDialog.alert("生成成功！");
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
                            MessageDialog.error("生成失败!");
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
}
