package com.github.git.domain;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * git Repository
 * @author imyuyu
 */
public class Repository {
    private File directory;
    private String defaultBranch;
    private List<String> localBranches;
    private List<String> remoteBranches;
    private List<Commit> commits;

    public File getDirectory() {
        return directory;
    }

    public void setDirectory(File directory) {
        this.directory = directory;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public List<String> getLocalBranches() {
        return localBranches;
    }

    public void setLocalBranches(List<String> localBranches) {
        this.localBranches = localBranches;
    }

    public List<String> getRemoteBranches() {
        return remoteBranches;
    }

    public void setRemoteBranches(List<String> remoteBranches) {
        this.remoteBranches = remoteBranches;
    }

    public List<Commit> getCommits() {
        return commits;
    }

    public void setCommits(List<Commit> commits) {
        this.commits = commits;
    }
}
