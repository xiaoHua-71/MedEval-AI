package com.xiaohua.model.dto.question;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
/**
 * @description: 好好学Java，早日找到好工作
 * @author: XiaoHua
 **/

public class QuestionContentDTO {

    //题目标题
    private String title;
    //题目选项列表
    private List<Option> options;


    /**
     * 题目选项
     * */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Option {
        private String result;
        private int score;
        private String value;
        private String key;
    }


}
