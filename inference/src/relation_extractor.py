import random
from typing import List, Dict


class RelationExtractor:
    def __init__(self):
        self.relation_types = {
            ('symptom', 'disease'): 'symptom_of_disease',
            ('disease', 'symptom'): 'disease_has_symptom',
            ('drug', 'disease'): 'drug_for_disease',
            ('test', 'disease'): 'test_for_disease',
            ('operation', 'disease'): 'operation_for_disease',
            ('anatomy', 'disease'): 'location_of_disease',
            ('drug', 'symptom'): 'drug_for_symptom',
            ('test', 'symptom'): 'test_for_symptom'
        }

    def extract(self, text: str, entities: List[Dict]) -> List[Dict]:
        relations = []
        entity_by_type = {}
        
        for i, entity in enumerate(entities):
            entity_type = entity['entity_type']
            if entity_type not in entity_by_type:
                entity_by_type[entity_type] = []
            entity_by_type[entity_type].append((i, entity))
        
        for (head_type, tail_type), relation_type in self.relation_types.items():
            if head_type in entity_by_type and tail_type in entity_by_type:
                for head_idx, head_entity in entity_by_type[head_type]:
                    for tail_idx, tail_entity in entity_by_type[tail_type]:
                        if head_idx == tail_idx:
                            continue
                        
                        distance = abs(head_entity['start_pos'] - tail_entity['start_pos'])
                        if distance < 100:
                            relation = {
                                'head_index': head_idx,
                                'tail_index': tail_idx,
                                'relation_type': relation_type,
                                'confidence': round(0.75 + random.random() * 0.2, 3)
                            }
                            relations.append(relation)
        
        return relations
