package com.github.git.patch;

import com.github.git.domain.Commit;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author imyuyu
 */
public class CommitTableHelper {

    private static List<TableColumn<Commit,String>> tableColumns = new ArrayList<>();

    static{

        TableColumn<Commit, String> hashColumn = new TableColumn<>("hash");
        hashColumn.setCellValueFactory(new PropertyValueFactory<Commit,String>("abbreviatedCommitHash"));
        hashColumn.setSortable(false);
        hashColumn.setMinWidth(100);


        TableColumn<Commit, String> authorColumn = new TableColumn<>("作者");
        authorColumn.setCellValueFactory(new PropertyValueFactory<Commit,String>("authorName"));
        authorColumn.setSortable(false);
        authorColumn.setMinWidth(100);


        TableColumn<Commit, String> emailColumn = new TableColumn<>("电子邮件");
        emailColumn.setCellValueFactory(new PropertyValueFactory<Commit,String>("authorEmail"));
        emailColumn.setSortable(false);
        emailColumn.setMinWidth(150);

        TableColumn<Commit, String> dateColumn = new TableColumn<>("日期");
        dateColumn.setCellValueFactory(new PropertyValueFactory<Commit,String>("authorDateRelative"));
        dateColumn.setSortable(false);
        dateColumn.setMinWidth(100);

        TableColumn<Commit, String> submitColumn = new TableColumn<>("主题");
        submitColumn.setCellValueFactory(new PropertyValueFactory<Commit,String>("subject"));
        submitColumn.setSortable(false);
        submitColumn.setMinWidth(200);


        tableColumns.add(hashColumn);
        tableColumns.add(authorColumn);
        tableColumns.add(emailColumn);
        tableColumns.add(dateColumn);
        tableColumns.add(submitColumn);
    }

    public static List<TableColumn<Commit,String>> getTableColumns(){
        return Collections.unmodifiableList(tableColumns);
    }

}
