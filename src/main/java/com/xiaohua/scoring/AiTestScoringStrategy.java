package com.xiaohua.scoring;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.xiaohua.manager.AiManager;
import com.xiaohua.model.dto.question.QuestionAnswerDTO;
import com.xiaohua.model.dto.question.QuestionContentDTO;
import com.xiaohua.model.entity.App;
import com.xiaohua.model.entity.Question;
import com.xiaohua.model.entity.UserAnswer;
import com.xiaohua.model.vo.QuestionVO;
import com.xiaohua.service.QuestionService;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @description: 好好学Java
 * @author: XiaoHua
 * 自定义测评类应用评分策略*
 **/
@ScoringStrategyConfig(appType = 1,scoringStrategy = 1)
public class AiTestScoringStrategy implements ScoringStrategy {

    @Resource
    private QuestionService questionService;

    @Resource
    private AiManager aiManager;

    @Resource
    private RedissonClient redissonClient;

    private static final String AI_ANSWER_LOCK = "AI_ANSWER_LOCK";
    /**
     * AI评分结果 本地缓存*
     */
    private final Cache<String, String> answerCacheMap =
            Caffeine.newBuilder().initialCapacity(1024)
                    // 缓存5分钟移除
                    .expireAfterAccess(5L, TimeUnit.MINUTES)
                    .build();


    //Ai评分系统消息
    private static final String AI_TEST_SCORING_SYSTEM_MESSAGE = "你是一位严谨的判题专家，我会给你如下信息：\n" +
            "```\n" +
            "应用名称，\n" +
            "【【【应用描述】】】，\n" +
            "题目和用户回答的列表：格式为 [{\"title\": \"题目\",\"answer\": \"用户回答\"}]\n" +
            "```\n" +
            "\n" +
            "请你根据上述信息，按照以下步骤来对用户进行评价：\n" +
            "1. 要求：需要给出一个明确的评价结果，包括评价名称（尽量简短）和评价描述（尽量详细，大于 200 字）\n" +
            "2. 严格按照下面的 json 格式输出评价名称和评价描述\n" +
            "```\n" +
            "{\"resultName\": \"评价名称\", \"resultDesc\": \"评价描述\"}\n" +
            "```\n" +
            "3. 返回格式必须为 JSON 对象";
    //alt + ctrl + v 提取app.getId()
    @Override
    public UserAnswer doScore(List<String> choices, App app) throws Exception {

        Long appId = app.getId();
        String jsonStr = JSONUtil.toJsonStr(choices);
        String cacheKey = buildCacheKey(appId, jsonStr);
        String answerJson = answerCacheMap.getIfPresent(cacheKey);
        //如果有缓存，直接返回
        if(StrUtil.isNotBlank(answerJson)){
            UserAnswer userAnswer = JSONUtil.toBean(answerJson, UserAnswer.class);
            userAnswer.setAppId(appId);
            userAnswer.setAppType(app.getAppType());
            userAnswer.setScoringStrategy(app.getScoringStrategy());
            userAnswer.setChoices(jsonStr);
            return userAnswer;
        }

        //定义锁
        RLock lock = redissonClient.getLock(AI_ANSWER_LOCK + cacheKey);

        try {
            //竞争锁
            boolean res = lock.tryLock(3, 15, TimeUnit.SECONDS);
            if(!res){
                return null;
            }

            //抢到锁了开始执行业务流程

            // 1. 根据 id 查询到题目
            Question question = questionService.getOne(
                    Wrappers.lambdaQuery(Question.class).eq(Question::getAppId, appId)
            );
            QuestionVO questionVO = QuestionVO.objToVo(question);
            List<QuestionContentDTO> questionContent = questionVO.getQuestionContent();
            // 2. 调用 AI 获取结果
            // 封装 Prompt
            String userMessage = getAiTestScoringUserMessage(app, questionContent, choices);
            // AI 生成
            String result = aiManager.doSyncStableRequest(AI_TEST_SCORING_SYSTEM_MESSAGE, userMessage);
            int start = result.indexOf("{");
            int end = result.lastIndexOf("}");
            String json = result.substring(start, end + 1);
            //缓存结果
            answerCacheMap.put(cacheKey,json);
            // 3. 构造返回值，填充答案对象的属性
            UserAnswer userAnswer = JSONUtil.toBean(json, UserAnswer.class);
            userAnswer.setAppId(appId);
            userAnswer.setAppType(app.getAppType());
            userAnswer.setScoringStrategy(app.getScoringStrategy());
            userAnswer.setChoices(JSONUtil.toJsonStr(choices));
            return userAnswer;

        }finally {
            if(lock != null && lock.isLocked()){
                if(lock.isHeldByCurrentThread()){
                    lock.unlock();
                }
            }

        }

        // 结果处理
        // 初始化 Jackson 的 ObjectMapper
//        ObjectMapper objectMapper = new ObjectMapper();
//        // 解析外层 JSON
//        JsonNode rootNode = objectMapper.readTree(result);
//        // 提取 message.content 中的嵌套 JSON 字符串
//        String content = rootNode.get("message").get("content").asText();
//        // 去掉 ```json 标记和多余字符
//        String innerJson = content.replace("```json", "").replace("```", "").trim();
//        // 解析内层 JSON
//        JsonNode innerNode = objectMapper.readTree(innerJson);
//        // 提取 resultName 和 resultDesc 的原始内容
//        String originalResultName = innerNode.get("resultName").asText();
//        String originalResultDesc = innerNode.get("resultDesc").asText();
//        // 构造新的 JSON 对象
//        JsonNode newJsonNode = objectMapper.createObjectNode()
//                .put("resultName", originalResultName)
//                .put("resultDesc", originalResultDesc);
//        // 转换为格式化 JSON 字符串
//        String outputJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(newJsonNode);
//        result = result.replace("\\n", "").trim();
    }


    private String getAiTestScoringUserMessage(App app, List<QuestionContentDTO> questionContentDTOList, List<String> choices) {
        StringBuilder userMessage = new StringBuilder();
        userMessage.append(app.getAppName()).append("\n");
        userMessage.append(app.getAppDesc()).append("\n");
        List<QuestionAnswerDTO> questionAnswerDTOList = new ArrayList<>();
        for (int i = 0; i < questionContentDTOList.size(); i++) {
            QuestionAnswerDTO questionAnswerDTO = new QuestionAnswerDTO();
            questionAnswerDTO.setTitle(questionContentDTOList.get(i).getTitle());
            questionAnswerDTO.setUserAnswer(choices.get(i));
            questionAnswerDTOList.add(questionAnswerDTO);
        }
        userMessage.append(JSONUtil.toJsonStr(questionAnswerDTOList));
        return userMessage.toString();
    }

    private String buildCacheKey(Long appId,String choices){
        return DigestUtil.md5Hex(appId + ":" + choices);

    }

}
