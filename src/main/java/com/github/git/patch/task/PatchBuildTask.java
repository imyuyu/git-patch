package com.github.git.patch.task;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.XmlUtil;
import cn.hutool.core.util.ZipUtil;
import com.github.git.common.Environment;
import com.github.git.common.SystemProperties;
import com.github.git.patch.PatchController;
import com.github.git.util.MessageDialog;
import com.github.git.util.ThreadHelper;
import com.github.git.util.WindowUtil;
import javafx.application.Platform;
import javafx.concurrent.Task;
import org.w3c.dom.NodeList;

import java.io.*;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author imyuyu
 */
public class PatchBuildTask extends Task<File> {
    private final Logger logger = Logger.getGlobal();

    private final List<String> names;
    private final String commitStartText;
    private final String commitEndText;
    private final String workspace;
    private final File workspaceFile;
    private final String saveFolder;
    private boolean isFullPackage;

    public PatchBuildTask(List<String> names, String commitStartText, String commitEndText,String workspace,String saveFolder) {
        this.names = names;
        this.commitStartText = commitStartText;
        this.commitEndText = commitEndText;
        this.workspace = workspace;
        this.workspaceFile = new File(workspace);
        this.saveFolder = saveFolder;
    }

    public boolean isFullPackage() {
        return isFullPackage;
    }

    public void setFullPackage(boolean fullPackage) {
        isFullPackage = fullPackage;
    }

    @Override
    protected File call() throws Exception {
        updateProgress(0,1);
        updateTitle("开始构建补丁包...");

        List<String> files = names.stream().filter(this::filterPath).collect(Collectors.toList());

        logger.info("一共变更文件" + files.size() + "个,开始进行补丁包制作。");

        updateMessage("一共变更文件" + files.size() + "个,开始进行补丁包制作。");

        String commitStartShort = commitStartText.substring(0, 8);
        String commitEndShort = commitEndText.substring(0, 8);

        String patchFileName = String.join("_", commitStartShort, commitEndShort, "patch");

        File patchDir = new File(Environment.getConfigFolder(), String.join(File.separator, "temp", patchFileName));

        if (!patchDir.exists()) {
            patchDir.mkdirs();
        }
        // 清空文件夹
        FileUtil.del(patchDir);

        File classesDir = new File(patchDir, "WEB-INF/classes");
        if (!classesDir.exists()) {
            classesDir.mkdirs();
        }

        long start = System.currentTimeMillis();
        logger.info("开始构建补丁包..");

        AtomicInteger atomicInteger = new AtomicInteger(0);

        files.parallelStream().forEach(new Consumer<String>() {
            @Override
            public void accept(String filePath) {

                updateTitle("开始构建补丁包..." + filePath);

                int idx4FileName = filePath.lastIndexOf("/");
                // 文件名
                String fileName = filePath.substring(idx4FileName + 1);

                int idx4ModuleName = filePath.indexOf("/");
                // 模块
                String moduleName = filePath.substring(0, idx4ModuleName);

                // 当前模块打包方式
                String packaging = "jar";
                // 解析一下pom
                File pomFile = new File(workspace + "/" + moduleName + "/pom.xml");
                if (pomFile.exists()) {
                    NodeList packagingNodes = XmlUtil.readXML(pomFile).getElementsByTagName("packaging");
                    if (packagingNodes.getLength() > 0) {
                        packaging = packagingNodes.item(packagingNodes.getLength() - 1).getTextContent();
                    }
                }

                int extIndex = fileName.lastIndexOf(".");
                String fileShortName = fileName.substring(0, extIndex);
                String fileExtName = fileName.substring(extIndex + 1);

                String sourcePath = moduleName + "/" + SystemProperties.getInstance().getSourceFolder();
                String resourcePath = moduleName + "/" + SystemProperties.getInstance().getResourceFolder();

                File sourceFile;
                File targetFile;

                if (isFullPackage && Objects.equals(packaging, "jar")) {

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

                    if (jars == null || jars.length == 0) {
                        return;
                    }

                    sourceFile = jars[0];

                    targetFile = new File(patchDir + "/WEB-INF/lib/" + sourceFile.getName());

                    if (targetFile.exists()) {
                        return;
                    }
                } else {
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
                    } else if (filePath.startsWith(resourcePath)) {

                        // resource 目录下的，直接放到class目录
                        String targetTemp = filePath.replace(resourcePath, "");
                        sourceFile = new File(workspaceFile, filePath);
                        targetFile = new File(classesDir, targetTemp);

                    } else {
                        // 不需要
                        String targetTemp = filePath.replace(moduleName + "/" + SystemProperties.getInstance().getWebappFolder(), "");
                        // 其他文件对应放,直接复制
                        sourceFile = new File(workspaceFile, filePath);
                        targetFile = new File(patchDir, targetTemp);
                    }
                }

                try {
                    if (sourceFile.exists()) {
                        File parentFile = targetFile.getParentFile();
                        if (!parentFile.exists()) {
                            parentFile.mkdirs();
                        }

                        if (targetFile.exists()) {
                            targetFile.delete();
                        }
                        targetFile.createNewFile();

                        IoUtil.copy(new FileInputStream(sourceFile), new FileOutputStream(targetFile));
                        logger.info("复制文件：" + sourceFile.getPath() + " > " + targetFile.getPath());
                    } else {
                        logger.info("源文件不存在,是被删除了:" + sourceFile.getPath());
                    }

                    double finalCount = atomicInteger.incrementAndGet();
                    updateMessage("复制文件：" + sourceFile.getPath() + " > " + targetFile.getPath() + ". 完成");
                    double percent = finalCount / files.size();
                    if (percent <= 0.95) {
                        updateProgress(percent,1);
                    }
                } catch (IOException e) {
                    logger.log(Level.SEVERE,"生成失败！",e);
                    throw new RuntimeException(e);
                }
            }
        });

        updateTitle("开始进行压缩...");
        updateMessage("开始进行压缩...");

        // 创建zip文件
        long zipStart = System.currentTimeMillis();
        File zipFile = new File(saveFolder, patchFileName + ".zip");
        File parentFile = zipFile.getParentFile();
        if (!parentFile.exists()) {
            parentFile.mkdirs();
        }
        ZipUtil.zip(zipFile, false, patchDir);

        String logInfo = "压缩完成,共耗时：" + (System.currentTimeMillis() - zipStart) + " MS";
        logger.info(logInfo);
        updateMessage(logInfo);
        Thread.sleep(100);
        updateTitle("清理临时文件...");
        updateMessage("清理临时文件...");
        logger.info("清理临时文件");
        // 清空文件夹
        long cl = System.currentTimeMillis();
        FileUtil.del(patchDir);
        logInfo = "清理临时文件完成.共耗时：" + (System.currentTimeMillis() - cl) + " MS";
        logger.info(logInfo);
        updateMessage(logInfo);

        updateProgress(1,1);
        updateTitle("构建补丁包完成...");

        logInfo = "构建补丁包：" + zipFile.getPath() + " 完成,共耗时：" + (System.currentTimeMillis() - start) + " MS";
        logger.info(logInfo);
        updateMessage(logInfo);

        return zipFile;
    }

    /**
     *
     * @param filePath
     * @return false表示验证失败
     */
    public boolean filterPath(String filePath){
        for (String s : SystemProperties.getInstance().getExcludedFile()) {
            boolean contains = filePath.contains(s) || s.contains(filePath);
            if(contains){
                logger.info("文件被过滤:"+filePath);
                return false;
            }
        }
        return true;
    }
}
