package com.github.git.util;

import cn.hutool.core.util.StrUtil;
import javafx.scene.control.TextField;

/**
 * @author imyuyu
 */
public class UIHelper {

    /**
     * 获取textField的值，会使用{@link String#trim()} 后再返回
     * @param textField
     * @return trim后的字符串
     */
    public static String val(TextField textField){
        String text = textField.getText();
        if(StrUtil.isNotBlank(text)){
            return text.trim();
        }
        return null;
    }
}
