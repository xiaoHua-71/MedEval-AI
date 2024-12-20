package com.xiaohua.scoring;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xiaohua.model.dto.question.QuestionContentDTO;
import com.xiaohua.model.entity.App;
import com.xiaohua.model.entity.Question;
import com.xiaohua.model.entity.ScoringResult;
import com.xiaohua.model.entity.UserAnswer;
import com.xiaohua.model.vo.QuestionVO;
import com.xiaohua.service.QuestionService;
import com.xiaohua.service.ScoringResultService;

import javax.annotation.Resource;
import java.util.List;
import java.util.Optional;

/**
 * @description: 好好学Java
 * @author: XiaoHua
 * 自定义打分应用评分策略*
 **/
@ScoringStrategyConfig(appType = 0,scoringStrategy = 0)
public class CustomScoreScoringStrategy implements ScoringStrategy {

    @Resource
    private QuestionService questionService;

    @Resource
    private ScoringResultService scoringResultService;
    @Override
    public UserAnswer doScore(List<String> choices, App app) throws Exception {
        Long appId = app.getId();
        //1.根据id查询到题目和题目结果信息
        Question question = questionService.getOne(
                Wrappers.lambdaQuery(Question.class)
                        .eq(Question::getAppId, appId)
        );

        List<ScoringResult> scoringResultList = scoringResultService.list(
                Wrappers.lambdaQuery(ScoringResult.class)
                        .eq(ScoringResult::getAppId, appId)
                        .orderByDesc(ScoringResult::getResultScoreRange)
        );

        //2.统计用户总得分

        int totalScore = 0;

        QuestionVO questionVO = QuestionVO.objToVo(question);
        List<QuestionContentDTO> questionContent = questionVO.getQuestionContent();
        //遍历题目列表
        // 遍历题目列表并同时遍历用户选择的答案
        for (int i = 0; i < questionContent.size(); i++) {
            QuestionContentDTO questionContentDTO = questionContent.get(i);
            String answer = choices.get(i);

            // 遍历题目中的选项
            for (QuestionContentDTO.Option option : questionContentDTO.getOptions()) {
                // 如果答案和选项的key匹配
                if (option.getKey().equals(answer)) {
                    int score = Optional.ofNullable(option.getScore()).orElse(0);
                    totalScore += score;
                    break; // 找到匹配项后退出循环，避免重复加分数
                }
            }
        }

        //3.遍历用户得分结果，找到第一个用户分数大于得分数得结果，作为最终结果
        ScoringResult maxScoringResult = scoringResultList.get(0);
        for(ScoringResult scoringResult : scoringResultList){
            if(totalScore >= scoringResult.getResultScoreRange()){
                maxScoringResult = scoringResult;
                break;
            }
        }
        //4.构造返回值，填充答案对象的属性
        // 返回最高分数和最高分数对应的评分结果
        UserAnswer userAnswer = new UserAnswer();

        userAnswer.setAppId(appId);
        userAnswer.setAppType(app.getAppType());
        userAnswer.setScoringStrategy(app.getScoringStrategy());
        userAnswer.setChoices(JSONUtil.toJsonStr(choices));
        userAnswer.setResultId(maxScoringResult.getId());
        userAnswer.setResultName(maxScoringResult.getResultName());
        userAnswer.setResultDesc(maxScoringResult.getResultDesc());
        userAnswer.setResultPicture(maxScoringResult.getResultPicture());
        userAnswer.setResultScore(totalScore);

        return userAnswer;
    }
}
