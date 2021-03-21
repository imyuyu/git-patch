package com.github.git.util;

import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * @author imyuyu
 */
public class MessageDialog {

    public static void alert(String msg){
        alert(Alert.AlertType.INFORMATION, msg);
    }

    public static void error(String msg){
        alert(Alert.AlertType.ERROR, msg);
    }

    public static void error(String msg,Throwable throwable){
        Alert information = new Alert(Alert.AlertType.ERROR, msg);
        information.setHeaderText(null);
        Label label = new Label("异常栈:");

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        String exceptionText = sw.toString();

        TextArea textArea = new TextArea(exceptionText);
        textArea.setEditable(false);
        textArea.setWrapText(true);

        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);
        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);

        GridPane expContent = new GridPane();
        expContent.setMaxWidth(Double.MAX_VALUE);
        expContent.add(label, 0, 0);
        expContent.add(textArea, 0, 1);
        // Set expandable Exception into the dialog pane.
        information.getDialogPane().setExpandableContent(expContent);
        Optional<ButtonType> result = information.showAndWait();
    }

    public static void warn(String msg){
        alert(Alert.AlertType.WARNING, msg);
    }


    public static void alert(Alert.AlertType alertType,String msg){
        Alert information = new Alert(alertType, msg);
        information.setHeaderText(null);
        Optional<ButtonType> result = information.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            //System.exit(0);
        }
    }

    public static String prompt(String tips, String selectedItem){
        TextInputDialog dialog = new TextInputDialog(selectedItem);
        dialog.setTitle("请输入");
        dialog.setHeaderText(null);
        dialog.setContentText(tips);

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()){
            return result.get();
        }
        return null;
    }

    public static void confirm(String msg, Consumer<ButtonType> ok){
        Alert information = new Alert(Alert.AlertType.CONFIRMATION, msg);
        information.setHeaderText(null);
        Optional<ButtonType> result = information.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            //System.exit(0);
            ok.accept(result.get());
        }
    }

    public static String notEmpty(TextField textField, String msg) throws IllegalArgumentException{
        String str = textField.getText().trim();
        if (Objects.equals(str, "")) {
            warn(msg);
            throw new IllegalArgumentException();
        }
        return str;
    }

    public static <T> T notNull(T o,String msg) throws IllegalArgumentException{
        if (o == null) {
            warn(msg);
            throw new IllegalArgumentException();
        }
        return o;
    }


}
