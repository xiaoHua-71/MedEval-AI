package com.xiaohua.model.dto.userAnswer;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 创建用户答案请求
 *
 * @author 小花
 * @from 好好学习
 */
@Data
public class UserAnswerAddRequest implements Serializable {


    /**
     * 题目id 防止幂等
     */
    private Long id;

    /**
     * 应用 id
     */
    private Long appId;

    /**
     * 用户答案（JSON 数组）
     */
    private List<String> choices;

    private static final long serialVersionUID = 1L;
}