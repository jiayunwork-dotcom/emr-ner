INSERT INTO users (username, password_hash, role, full_name, email)
VALUES 
    ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'admin', '系统管理员', 'admin@example.com'),
    ('doctor1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'annotator', '张医生', 'doctor1@example.com'),
    ('doctor2', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'annotator', '李医生', 'doctor2@example.com')
ON CONFLICT (username) DO NOTHING;

INSERT INTO model_versions (version_name, model_type, description, is_active, metrics)
VALUES 
    ('bert-crf-v1.0', 'BERT-CRF', '基于中文BERT预训练模型的CRF序列标注模型，支持7类医学实体识别', true, 
     '{"overall": {"precision": 0.89, "recall": 0.87, "f1": 0.88}, "by_type": {"disease": {"precision": 0.91, "recall": 0.89, "f1": 0.90}, "symptom": {"precision": 0.87, "recall": 0.85, "f1": 0.86}, "drug": {"precision": 0.92, "recall": 0.90, "f1": 0.91}, "test": {"precision": 0.88, "recall": 0.86, "f1": 0.87}, "operation": {"precision": 0.85, "recall": 0.82, "f1": 0.83}, "anatomy": {"precision": 0.90, "recall": 0.88, "f1": 0.89}, "time": {"precision": 0.93, "recall": 0.91, "f1": 0.92}}}'),
    ('bilstm-crf-v1.0', 'BiLSTM-CRF', '轻量级BiLSTM-CRF模型，适合CPU环境快速推理', false,
     '{"overall": {"precision": 0.82, "recall": 0.80, "f1": 0.81}}')
ON CONFLICT (version_name) DO NOTHING;
