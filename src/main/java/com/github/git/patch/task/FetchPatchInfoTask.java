package com.github.git.patch.task;

import cn.hutool.core.io.IoUtil;
import com.github.git.util.MessageDialog;
import com.github.git.util.ThreadHelper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author imyuyu
 */
public class FetchPatchInfoTask extends Task<List<String>> {
    private final Logger logger = Logger.getGlobal();

    private final String gitExecutable;
    private final String commitStartText;
    private final String commitEndText;
    private final File workspace;

    public FetchPatchInfoTask(String gitExecutable, String commitStartText, String commitEndText,File workspace) {
        this.gitExecutable = gitExecutable;
        this.commitStartText = commitStartText;
        this.commitEndText = commitEndText;
        this.workspace = workspace;
    }

    @Override
    protected List<String> call() throws Exception {

        logger.info("开始获取变更详情...");
        long millis = System.currentTimeMillis();

        List<String> names = FXCollections.observableArrayList();
        int i;
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
        processBuilder.directory(workspace);
        Map<String, String> environment = processBuilder.environment();
        Process process = null;
        try {
            process = processBuilder.start();

            Process finalProcess = process;

            ThreadHelper.submit(new Runnable() {
                @Override
                public void run() {
                    InputStream inputStream = finalProcess.getInputStream();
                    IoUtil.readUtf8Lines(inputStream,names);
                }
            });

            i = process.waitFor();
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }


        logger.info("获取变更详情完成，耗时："+(System.currentTimeMillis() - millis)+" MS");


        if(i == 0){
            return names;
        }else{
            InputStream inputStream = process.getErrorStream();
            String s = IoUtil.readUtf8(inputStream);
            String message;

            logger.severe("生成失败,错误原因：\n" + s);
            if (s.contains("unknown revision or path not in the working tree.")) {
                message = "生成失败,提交hash不存在！";
            } else {
                message = "生成失败";
            }
            throw new RuntimeException(message);
        }
    }
}
