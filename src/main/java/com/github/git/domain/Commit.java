package com.github.git.domain;

import java.util.Date;

/**
 * @author imyuyu
 */
public class Commit {
    private String commitHash ;
    private String abbreviatedCommitHash;
    private String authorName;
    private String authorEmail;
    private Date authorDate;
    private String authorDateRelative;
    private String committerName;
    private String committerEmail;
    private String committerDate;
    private String subject;

    public String getCommitHash() {
        return commitHash;
    }

    public void setCommitHash(String commitHash) {
        this.commitHash = commitHash;
    }

    public String getAbbreviatedCommitHash() {
        return abbreviatedCommitHash;
    }

    public void setAbbreviatedCommitHash(String abbreviatedCommitHash) {
        this.abbreviatedCommitHash = abbreviatedCommitHash;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getAuthorEmail() {
        return authorEmail;
    }

    public void setAuthorEmail(String authorEmail) {
        this.authorEmail = authorEmail;
    }

    public Date getAuthorDate() {
        return authorDate;
    }

    public void setAuthorDate(Date authorDate) {
        this.authorDate = authorDate;
    }

    public String getAuthorDateRelative() {
        return authorDateRelative;
    }

    public void setAuthorDateRelative(String authorDateRelative) {
        this.authorDateRelative = authorDateRelative;
    }

    public String getCommitterName() {
        return committerName;
    }

    public void setCommitterName(String committerName) {
        this.committerName = committerName;
    }

    public String getCommitterEmail() {
        return committerEmail;
    }

    public void setCommitterEmail(String committerEmail) {
        this.committerEmail = committerEmail;
    }

    public String getCommitterDate() {
        return committerDate;
    }

    public void setCommitterDate(String committerDate) {
        this.committerDate = committerDate;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }
}
