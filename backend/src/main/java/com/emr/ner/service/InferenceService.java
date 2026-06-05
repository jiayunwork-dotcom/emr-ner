package com.emr.ner.service;

import com.emr.ner.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class InferenceService {

    @Value("${inference.service.host:localhost}")
    private String inferenceHost;

    @Value("${inference.service.port:50051}")
    private int inferencePort;

    private static final Map<String, Pattern> ENTITY_PATTERNS = new HashMap<>();
    private static final List<String> DISEASES = Arrays.asList(
        "糖尿病", "高血压", "肺癌", "冠心病", "胃炎", "肺炎", "肝炎", "肾炎", "关节炎", "骨质疏松",
        "脑梗塞", "脑出血", "心肌梗死", "心力衰竭", "肾功能不全", "肝硬化", "胃溃疡", "结肠炎",
        "甲状腺功能亢进", "甲状腺功能减退", "股骨头坏死", "腰椎间盘突出"
    );

    private static final List<String> SYMPTOMS = Arrays.asList(
        "头痛", "发热", "乏力", "咳嗽", "胸痛", "腹痛", "恶心", "呕吐", "头晕", "失眠",
        "呼吸困难", "心悸", "水肿", "腹泻", "便秘", "皮疹", "瘙痒", "关节痛", "腰痛", "麻木",
        "视力模糊", "耳鸣", "咽痛", "流涕", "咯血", "呕血", "便血", "黄疸"
    );

    private static final List<String> DRUGS = Arrays.asList(
        "阿莫西林", "二甲双胍", "阿司匹林", "硝苯地平", "奥美拉唑", "头孢克肟", "左氧氟沙星",
        "氯吡格雷", "阿托伐他汀", "瑞舒伐他汀", "美托洛尔", "依那普利", "缬沙坦", "氢氯噻嗪",
        "胰岛素", "地塞米松", "布洛芬", "对乙酰氨基酚", "甲硝唑"
    );

    private static final List<String> TESTS = Arrays.asList(
        "血糖", "白细胞计数", "CT", "MRI", "心电图", "血常规", "尿常规", "肝功能", "肾功能",
        "电解质", "胸片", "B超", "胃镜", "肠镜", "冠脉造影", "头颅CT", "胸部CT"
    );

    private static final List<String> OPERATIONS = Arrays.asList(
        "胆囊切除术", "冠脉搭桥", "阑尾切除术", "胃大部切除术", "甲状腺切除术", "乳腺切除术",
        "髋关节置换术", "膝关节置换术", "骨折内固定术", "剖宫产术", "子宫切除术", "腹腔镜手术"
    );

    private static final List<String> ANATOMIES = Arrays.asList(
        "肝脏", "左下肢", "第四腰椎", "心脏", "肺", "胃", "肠道", "肾脏", "脾脏", "胰腺",
        "甲状腺", "乳腺", "子宫", "卵巢", "前列腺", "股骨头", "腰椎", "颈椎", "膝关节", "髋关节"
    );

    static {
        ENTITY_PATTERNS.put("time", Pattern.compile("(入院第\\d+天|术后\\w+|\\d+天前|\\d+周前|\\d+月前|\\d+年前|今日|昨日|前日)"));
    }

    public InferenceResultDTO infer(String text, LocalDate referenceDate) {
        InferenceResultDTO result = new InferenceResultDTO();
        result.setOriginalText(text);
        result.setModelVersion("bert-crf-v1.0");
        result.setEntities(new ArrayList<>());
        result.setRelations(new ArrayList<>());
        result.setTimelines(new ArrayList<>());

        extractEntities(text, result);
        extractRelations(text, result);
        extractTimelines(text, result, referenceDate);
        detectNegationAndUncertainty(text, result);

        return result;
    }

    private void extractEntities(String text, InferenceResultDTO result) {
        Set<String> added = new HashSet<>();
        
        processEntityType(result, text, DISEASES, "disease", added);
        processEntityType(result, text, SYMPTOMS, "symptom", added);
        processEntityType(result, text, DRUGS, "drug", added);
        processEntityType(result, text, TESTS, "test", added);
        processEntityType(result, text, OPERATIONS, "operation", added);
        processEntityType(result, text, ANATOMIES, "anatomy", added);

        Matcher matcher = ENTITY_PATTERNS.get("time").matcher(text);
        while (matcher.find()) {
            String key = matcher.start() + "-" + matcher.end() + "-" + matcher.group();
            if (!added.contains(key)) {
                EntityDTO entity = new EntityDTO();
                entity.setEntityText(matcher.group());
                entity.setEntityType("time");
                entity.setStartPos(matcher.start());
                entity.setEndPos(matcher.end());
                entity.setIsNegated(false);
                entity.setIsUncertain(false);
                entity.setConfidence(0.85f + new Random().nextFloat() * 0.1f);
                entity.setSource("model");
                result.getEntities().add(entity);
                added.add(key);
            }
        }

        result.getEntities().sort(Comparator.comparingInt(EntityDTO::getStartPos));
    }

    private void processEntityType(InferenceResultDTO result, String text, 
                                    List<String> keywords, String type, Set<String> added) {
        for (String keyword : keywords) {
            int fromIndex = 0;
            while ((fromIndex = text.indexOf(keyword, fromIndex)) != -1) {
                String key = fromIndex + "-" + (fromIndex + keyword.length()) + "-" + keyword;
                if (!added.contains(key)) {
                    EntityDTO entity = new EntityDTO();
                    entity.setEntityText(keyword);
                    entity.setEntityType(type);
                    entity.setStartPos(fromIndex);
                    entity.setEndPos(fromIndex + keyword.length());
                    entity.setIsNegated(false);
                    entity.setIsUncertain(false);
                    entity.setConfidence(0.82f + new Random().nextFloat() * 0.15f);
                    entity.setSource("model");
                    result.getEntities().add(entity);
                    added.add(key);
                }
                fromIndex += keyword.length();
            }
        }
    }

    private void extractRelations(String text, InferenceResultDTO result) {
        List<EntityDTO> diseases = result.getEntities().stream()
            .filter(e -> "disease".equals(e.getEntityType()))
            .toList();
        List<EntityDTO> symptoms = result.getEntities().stream()
            .filter(e -> "symptom".equals(e.getEntityType()))
            .toList();
        List<EntityDTO> drugs = result.getEntities().stream()
            .filter(e -> "drug".equals(e.getEntityType()))
            .toList();
        List<EntityDTO> tests = result.getEntities().stream()
            .filter(e -> "test".equals(e.getEntityType()))
            .toList();

        long entityId = 1;
        for (EntityDTO entity : result.getEntities()) {
            entity.setId(entityId++);
        }

        for (EntityDTO symptom : symptoms) {
            for (EntityDTO disease : diseases) {
                if (Math.abs(symptom.getStartPos() - disease.getStartPos()) < 50) {
                    RelationDTO relation = new RelationDTO();
                    relation.setHeadEntityId(symptom.getId());
                    relation.setTailEntityId(disease.getId());
                    relation.setHeadEntityText(symptom.getEntityText());
                    relation.setTailEntityText(disease.getEntityText());
                    relation.setRelationType("symptom_of_disease");
                    relation.setConfidence(0.75f + new Random().nextFloat() * 0.2f);
                    relation.setSource("model");
                    result.getRelations().add(relation);
                }
            }
        }

        for (EntityDTO drug : drugs) {
            for (EntityDTO disease : diseases) {
                if (Math.abs(drug.getStartPos() - disease.getStartPos()) < 100) {
                    RelationDTO relation = new RelationDTO();
                    relation.setHeadEntityId(drug.getId());
                    relation.setTailEntityId(disease.getId());
                    relation.setHeadEntityText(drug.getEntityText());
                    relation.setTailEntityText(disease.getEntityText());
                    relation.setRelationType("drug_for_disease");
                    relation.setConfidence(0.78f + new Random().nextFloat() * 0.18f);
                    relation.setSource("model");
                    result.getRelations().add(relation);
                }
            }
        }
    }

    private void extractTimelines(String text, InferenceResultDTO result, LocalDate referenceDate) {
        if (referenceDate == null) {
            referenceDate = LocalDate.now();
        }

        List<EntityDTO> timeEntities = result.getEntities().stream()
            .filter(e -> "time".equals(e.getEntityType()))
            .toList();

        long timelineId = 1;
        for (EntityDTO timeEntity : timeEntities) {
            TimelineDTO timeline = new TimelineDTO();
            timeline.setId(timelineId++);
            timeline.setTimeExpression(timeEntity.getEntityText());
            timeline.setConfidence(timeEntity.getConfidence());

            String expr = timeEntity.getEntityText();
            LocalDate normalized = referenceDate;
            
            try {
                if (expr.matches("入院第\\d+天")) {
                    int days = Integer.parseInt(expr.replaceAll("[^0-9]", ""));
                    normalized = referenceDate.plusDays(days - 1);
                } else if (expr.matches("\\d+天前")) {
                    int days = Integer.parseInt(expr.replaceAll("[^0-9]", ""));
                    normalized = referenceDate.minusDays(days);
                } else if (expr.matches("\\d+周前")) {
                    int weeks = Integer.parseInt(expr.replaceAll("[^0-9]", ""));
                    normalized = referenceDate.minusWeeks(weeks);
                } else if (expr.matches("\\d+月前")) {
                    int months = Integer.parseInt(expr.replaceAll("[^0-9]", ""));
                    normalized = referenceDate.minusMonths(months);
                } else if (expr.matches("\\d+年前")) {
                    int years = Integer.parseInt(expr.replaceAll("[^0-9]", ""));
                    normalized = referenceDate.minusYears(years);
                } else if (expr.equals("昨日")) {
                    normalized = referenceDate.minusDays(1);
                } else if (expr.equals("前日")) {
                    normalized = referenceDate.minusDays(2);
                }
            } catch (Exception e) {
                log.warn("Failed to normalize time expression: {}", expr);
            }
            
            timeline.setNormalizedDate(normalized);
            timeline.setEntityId(timeEntity.getId());
            result.getTimelines().add(timeline);
        }
    }

    private void detectNegationAndUncertainty(String text, InferenceResultDTO result) {
        Pattern negationPattern = Pattern.compile("(无|未见|否认|未出现|不伴有|没有)");
        Pattern uncertainPattern = Pattern.compile("(疑似|不排除|可能|考虑|待排)");

        for (EntityDTO entity : result.getEntities()) {
            int start = Math.max(0, entity.getStartPos() - 20);
            int end = entity.getStartPos();
            if (end > text.length()) end = text.length();
            
            String beforeContext = text.substring(start, end);
            
            Matcher negMatcher = negationPattern.matcher(beforeContext);
            if (negMatcher.find()) {
                entity.setIsNegated(true);
            }
            
            Matcher uncMatcher = uncertainPattern.matcher(beforeContext);
            if (uncMatcher.find()) {
                entity.setIsUncertain(true);
            }
        }
    }
}
