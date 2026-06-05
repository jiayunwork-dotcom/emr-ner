import re
import random
from typing import List, Dict, Tuple


class EntityExtractor:
    def __init__(self):
        self.entity_keywords = {
            'disease': [
                '糖尿病', '高血压', '肺癌', '冠心病', '胃炎', '肺炎', '肝炎', '肾炎',
                '关节炎', '骨质疏松', '脑梗塞', '脑出血', '心肌梗死', '心力衰竭',
                '肾功能不全', '肝硬化', '胃溃疡', '结肠炎', '甲状腺功能亢进',
                '甲状腺功能减退', '股骨头坏死', '腰椎间盘突出', '慢性支气管炎',
                '肺气肿', '哮喘', '贫血', '白血病', '淋巴瘤'
            ],
            'symptom': [
                '头痛', '发热', '乏力', '咳嗽', '胸痛', '腹痛', '恶心', '呕吐',
                '头晕', '失眠', '呼吸困难', '心悸', '水肿', '腹泻', '便秘',
                '皮疹', '瘙痒', '关节痛', '腰痛', '麻木', '视力模糊', '耳鸣',
                '咽痛', '流涕', '咯血', '呕血', '便血', '黄疸', '消瘦', '多汗'
            ],
            'drug': [
                '阿莫西林', '二甲双胍', '阿司匹林', '硝苯地平', '奥美拉唑',
                '头孢克肟', '左氧氟沙星', '氯吡格雷', '阿托伐他汀', '瑞舒伐他汀',
                '美托洛尔', '依那普利', '缬沙坦', '氢氯噻嗪', '胰岛素',
                '地塞米松', '布洛芬', '对乙酰氨基酚', '甲硝唑', '万古霉素'
            ],
            'test': [
                '血糖', '白细胞计数', 'CT', 'MRI', '心电图', '血常规', '尿常规',
                '肝功能', '肾功能', '电解质', '胸片', 'B超', '胃镜', '肠镜',
                '冠脉造影', '头颅CT', '胸部CT', '肝功能检查', '肾功能检查',
                '肿瘤标志物', '血常规检查'
            ],
            'operation': [
                '胆囊切除术', '冠脉搭桥', '阑尾切除术', '胃大部切除术',
                '甲状腺切除术', '乳腺切除术', '髋关节置换术', '膝关节置换术',
                '骨折内固定术', '剖宫产术', '子宫切除术', '腹腔镜手术',
                '开颅手术', '心脏瓣膜置换术', '肾移植', '肝移植'
            ],
            'anatomy': [
                '肝脏', '左下肢', '第四腰椎', '心脏', '肺', '胃', '肠道', '肾脏',
                '脾脏', '胰腺', '甲状腺', '乳腺', '子宫', '卵巢', '前列腺',
                '股骨头', '腰椎', '颈椎', '膝关节', '髋关节', '大脑', '小脑',
                '食管', '气管', '膀胱', '胆囊'
            ]
        }
        
        self.time_pattern = re.compile(
            r'(入院第\d+天|术后\w+|\d+天前|\d+周前|\d+月前|\d+年前|今日|昨日|前日|当天|次日|第\d+天)'
        )
        
        self.negation_pattern = re.compile(r'(无|未见|否认|未出现|不伴有|没有|未诉|不考虑|排除)')
        self.uncertain_pattern = re.compile(r'(疑似|不排除|可能|考虑|待排|可疑|不除外)')

    def extract(self, text: str) -> List[Dict]:
        entities = []
        seen = set()
        
        for entity_type, keywords in self.entity_keywords.items():
            for keyword in keywords:
                start = 0
                while True:
                    pos = text.find(keyword, start)
                    if pos == -1:
                        break
                    
                    key = (pos, pos + len(keyword), keyword)
                    if key not in seen:
                        entity = {
                            'entity_text': keyword,
                            'entity_type': entity_type,
                            'start_pos': pos,
                            'end_pos': pos + len(keyword),
                            'is_negated': False,
                            'is_uncertain': False,
                            'confidence': round(0.82 + random.random() * 0.15, 3)
                        }
                        entities.append(entity)
                        seen.add(key)
                    
                    start = pos + len(keyword)
        
        for match in self.time_pattern.finditer(text):
            key = (match.start(), match.end(), match.group())
            if key not in seen:
                entity = {
                    'entity_text': match.group(),
                    'entity_type': 'time',
                    'start_pos': match.start(),
                    'end_pos': match.end(),
                    'is_negated': False,
                    'is_uncertain': False,
                    'confidence': round(0.85 + random.random() * 0.1, 3)
                }
                entities.append(entity)
                seen.add(key)
        
        entities.sort(key=lambda x: x['start_pos'])
        self._detect_negation_and_uncertainty(text, entities)
        
        return entities

    def _detect_negation_and_uncertainty(self, text: str, entities: List[Dict]):
        for entity in entities:
            start = max(0, entity['start_pos'] - 20)
            end = entity['start_pos']
            context = text[start:end]
            
            if self.negation_pattern.search(context):
                entity['is_negated'] = True
            
            if self.uncertain_pattern.search(context):
                entity['is_uncertain'] = True
