package com.github.git.util;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.github.git.common.Environment;
import com.github.git.common.ThreadHelper;
import com.github.git.common.ui.MessageDialog;
import com.github.git.domain.Commit;
import com.github.git.domain.Repository;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author imyuyu
 */
public class GitUtil {

    private static Logger logger = Logger.getGlobal();

    public static Repository initRepository(File directory){
        if(!directory.exists()){
            throw new IllegalArgumentException("git仓库地址不存在！");
        }

        String gitExecutable = Environment.getGitExecutable();
        if(StrUtil.isBlank(gitExecutable)){
            throw new IllegalArgumentException("未配置git可执行文件路径，请先配置可执行文件路径！");
        }

        File gitExecutableFile = new File(gitExecutable);
        if(!gitExecutableFile.exists()){
            throw new IllegalArgumentException("git可执行文件不存在！");
        }

        CountDownLatch countDownLatch = new CountDownLatch(2);

        Repository repository = new Repository();
        repository.setDirectory(directory);
        loadCommitHistory(directory, new Consumer<List<Commit>>() {
            @Override
            public void accept(List<Commit> commits) {
                repository.setCommits(commits);
                countDownLatch.countDown();
            }
        });

        loadBranches(directory, new Consumer<List<String>>() {
            @Override
            public void accept(List<String> branches) {
                List<String> localBranches = new ArrayList();
                List<String> remoteBranches = new ArrayList();
                branches.forEach(s -> {
                    String name = s.trim();
                    if(name.startsWith("*")){
                        String defaultName = name.replace("*", "").trim();
                        repository.setDefaultBranch(defaultName);
                        localBranches.add(defaultName);
                    }else if(name.startsWith("remotes/")){
                        name = name.replace("remotes/", "");
                        if(name.contains("->")){
                            String[] split = name.split("->");
                            remoteBranches.add(split[1]);
                        }else{
                            remoteBranches.add(name);
                        }
                    }else{
                        localBranches.add(name);
                    }
                });
                repository.setLocalBranches(localBranches);
                repository.setRemoteBranches(remoteBranches);
                countDownLatch.countDown();
            }
        });
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return  repository;
    }
    /**
     * 初始化仓库
     */
    public static Repository initRepository(String directory){

        if(StrUtil.isBlank(directory)){
            throw new IllegalArgumentException("git仓库地址不能为空！");
        }

        return initRepository(new File(directory));
    }

    private static void loadCommitHistory(File directory, Consumer<List<Commit>> consumer){
        String gitExecutable = Environment.getGitExecutable();

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(
                gitExecutable
                , "--no-pager"
                ,"log"
                ,"--pretty=format:'%H|%h|%an|%ae|%ar|%ad|%s'"
                ,"--date=format:'%Y-%m-%d %H:%M:%S'"
        );
        processBuilder.directory(directory);
        Map<String, String> environment = processBuilder.environment();

        List<String> commits = new ArrayList<>();

        try {
            Process process = processBuilder.start();

            logger.info("开始获取仓库【"+directory.getPath()+"】的提交历史...");

            ThreadHelper.submit(() -> {
                InputStream inputStream = process.getInputStream();
                IoUtil.readUtf8Lines(inputStream,commits);
            });

            ThreadHelper.submit(new Runnable() {
                @Override
                public void run() {
                    long l = System.currentTimeMillis();
                    int i = 0;
                    try {
                        i = process.waitFor();
                    }catch (Exception e){

                    }

                    if(i != 0){
                        InputStream inputStream = process.getErrorStream();
                        String s = IoUtil.readUtf8(inputStream);
                        logger.log(Level.SEVERE,"获取仓库【"+directory.getPath()+"】的提交历史出现错误！",new RuntimeException(s));
                        throw new RuntimeException("获取仓库提交历史错误,清查阅日志");
                    }

                    logger.info("获取仓库【"+directory.getPath()+"】的提交历史完成，共耗时:"+(System.currentTimeMillis()-l)+" MS");

                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                    List<Commit> commitList = commits.stream().map(new Function<String, Commit>() {
                        @Override
                        public Commit apply(String s) {
                            String[] commitInfo = s.split("\\|");
                            Commit commit = new Commit();
                            commit.setCommitHash(commitInfo[0]);
                            commit.setAbbreviatedCommitHash(commitInfo[1]);
                            commit.setAuthorName(commitInfo[2]);
                            commit.setAuthorEmail(commitInfo[3]);
                            commit.setAuthorDateRelative(commitInfo[4]);
                            try {
                                commit.setAuthorDate(sdf.parse(commitInfo[5]));
                            } catch (ParseException e) {
                            }
                            commit.setSubject(commitInfo[6]);
                            return commit;
                        }
                    }).collect(Collectors.toList());


                    consumer.accept(commitList);
                }
            });
        } catch (IOException e) {
            logger.log(Level.SEVERE,"获取仓库【"+directory.getPath()+"】的提交历史出现错误！",e);
            throw new RuntimeException("获取仓库提交历史错误,清查阅日志");
        }
    }

    private static void loadBranches(File directory, Consumer<List<String>> consumer){
        String gitExecutable = Environment.getGitExecutable();

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(
                gitExecutable
                ,"branch"
                ,"-a"
        );
        processBuilder.directory(directory);
        Map<String, String> environment = processBuilder.environment();

        List<String> branches = new ArrayList<>();

        try {
            Process process = processBuilder.start();

            logger.info("开始获取仓库【"+directory.getPath()+"】的分支数据...");

            ThreadHelper.submit(() -> {
                InputStream inputStream = process.getInputStream();
                IoUtil.readUtf8Lines(inputStream,branches);
            });

            ThreadHelper.submit(new Runnable() {
                @Override
                public void run() {
                    long l = System.currentTimeMillis();
                    int i = 0;
                    try {
                        i = process.waitFor();
                    }catch (Exception e){

                    }

                    if(i != 0){
                        InputStream inputStream = process.getErrorStream();
                        String s = IoUtil.readUtf8(inputStream);
                        logger.log(Level.SEVERE,"获取仓库【"+directory.getPath()+"】的分支数据出现错误！",new RuntimeException(s));
                        throw new RuntimeException("获取仓库分支数据错误,清查阅日志");
                    }

                    logger.info("获取仓库【"+directory.getPath()+"】的分支数据完成，共耗时:"+(System.currentTimeMillis()-l)+" MS");

                    consumer.accept(branches);
                }
            });
        } catch (IOException e) {
            logger.log(Level.SEVERE,"获取仓库【"+directory.getPath()+"】的分支数据出现错误！",e);
            throw new RuntimeException("获取仓库分支数据错误,清查阅日志");
        }
    }
}
