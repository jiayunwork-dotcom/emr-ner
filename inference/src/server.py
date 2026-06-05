import grpc
from concurrent import futures
import time
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import inference_pb2
import inference_pb2_grpc
from ner_extractor import EntityExtractor
from relation_extractor import RelationExtractor
from timeline_extractor import TimelineExtractor


class InferenceService(inference_pb2_grpc.InferenceServiceServicer):
    def __init__(self):
        self.entity_extractor = EntityExtractor()
        self.relation_extractor = RelationExtractor()
        self.timeline_extractor = TimelineExtractor()
        self.model_version = "bert-crf-v1.0"

    def ExtractEntities(self, request, context):
        start_time = time.time()
        
        entities = self.entity_extractor.extract(request.text)
        
        latency_ms = (time.time() - start_time) * 1000
        
        response = inference_pb2.ExtractEntitiesResponse()
        response.model_version = self.model_version
        response.latency_ms = latency_ms
        
        for ent in entities:
            entity = inference_pb2.Entity(
                entity_text=ent['entity_text'],
                entity_type=ent['entity_type'],
                start_pos=ent['start_pos'],
                end_pos=ent['end_pos'],
                is_negated=ent['is_negated'],
                is_uncertain=ent['is_uncertain'],
                confidence=ent['confidence']
            )
            response.entities.append(entity)
        
        return response

    def ExtractRelations(self, request, context):
        start_time = time.time()
        
        entities_list = []
        for ent in request.entities:
            entities_list.append({
                'entity_text': ent.entity_text,
                'entity_type': ent.entity_type,
                'start_pos': ent.start_pos,
                'end_pos': ent.end_pos
            })
        
        relations = self.relation_extractor.extract(request.text, entities_list)
        
        latency_ms = (time.time() - start_time) * 1000
        
        response = inference_pb2.ExtractRelationsResponse()
        response.model_version = self.model_version
        response.latency_ms = latency_ms
        
        for rel in relations:
            relation = inference_pb2.Relation(
                head_index=rel['head_index'],
                tail_index=rel['tail_index'],
                relation_type=rel['relation_type'],
                confidence=rel['confidence']
            )
            response.relations.append(relation)
        
        return response

    def ExtractTimelines(self, request, context):
        start_time = time.time()
        
        entities_list = []
        for ent in request.entities:
            entities_list.append({
                'entity_text': ent.entity_text,
                'entity_type': ent.entity_type,
                'start_pos': ent.start_pos,
                'end_pos': ent.end_pos
            })
        
        timelines = self.timeline_extractor.extract(
            request.text, entities_list, request.reference_date
        )
        
        latency_ms = (time.time() - start_time) * 1000
        
        response = inference_pb2.ExtractTimelinesResponse()
        response.model_version = self.model_version
        response.latency_ms = latency_ms
        
        for tl in timelines:
            timeline = inference_pb2.Timeline(
                time_expression=tl['time_expression'],
                normalized_date=tl['normalized_date'] or '',
                associated_event=tl['associated_event'] or '',
                entity_index=tl['entity_index'],
                confidence=tl['confidence']
            )
            response.timelines.append(timeline)
        
        return response

    def FullInference(self, request, context):
        start_time = time.time()
        
        entities = self.entity_extractor.extract(request.text)
        relations = self.relation_extractor.extract(request.text, entities)
        timelines = self.timeline_extractor.extract(
            request.text, entities, request.reference_date
        )
        
        latency_ms = (time.time() - start_time) * 1000
        
        response = inference_pb2.FullInferenceResponse()
        response.original_text = request.text
        response.model_version = self.model_version
        response.latency_ms = latency_ms
        
        for ent in entities:
            entity = inference_pb2.Entity(
                entity_text=ent['entity_text'],
                entity_type=ent['entity_type'],
                start_pos=ent['start_pos'],
                end_pos=ent['end_pos'],
                is_negated=ent['is_negated'],
                is_uncertain=ent['is_uncertain'],
                confidence=ent['confidence']
            )
            response.entities.append(entity)
        
        for rel in relations:
            relation = inference_pb2.Relation(
                head_index=rel['head_index'],
                tail_index=rel['tail_index'],
                relation_type=rel['relation_type'],
                confidence=rel['confidence']
            )
            response.relations.append(relation)
        
        for tl in timelines:
            timeline = inference_pb2.Timeline(
                time_expression=tl['time_expression'],
                normalized_date=tl['normalized_date'] or '',
                associated_event=tl['associated_event'] or '',
                entity_index=tl['entity_index'],
                confidence=tl['confidence']
            )
            response.timelines.append(timeline)
        
        return response


def serve():
    port = os.getenv('GRPC_PORT', '50051')
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    inference_pb2_grpc.add_InferenceServiceServicer_to_server(
        InferenceService(), server
    )
    server.add_insecure_port(f'[::]:{port}')
    server.start()
    print(f'Inference gRPC server started on port {port}')
    server.wait_for_termination()


if __name__ == '__main__':
    serve()
