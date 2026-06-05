CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'annotator',
    full_name VARCHAR(100),
    email VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS documents (
    id SERIAL PRIMARY KEY,
    document_type VARCHAR(50) NOT NULL,
    title VARCHAR(255),
    content TEXT NOT NULL,
    patient_id VARCHAR(100),
    visit_id VARCHAR(100),
    admission_date DATE,
    discharge_date DATE,
    reference_date DATE,
    status VARCHAR(20) DEFAULT 'pending',
    created_by INTEGER REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    model_version VARCHAR(50)
);

CREATE INDEX idx_documents_patient ON documents(patient_id);
CREATE INDEX idx_documents_status ON documents(status);

CREATE TABLE IF NOT EXISTS entities (
    id SERIAL PRIMARY KEY,
    document_id INTEGER NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    entity_text TEXT NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    start_pos INTEGER NOT NULL,
    end_pos INTEGER NOT NULL,
    is_negated BOOLEAN DEFAULT FALSE,
    is_uncertain BOOLEAN DEFAULT FALSE,
    confidence FLOAT,
    source VARCHAR(20) DEFAULT 'model',
    annotated_by INTEGER REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_entities_document ON entities(document_id);
CREATE INDEX idx_entities_type ON entities(entity_type);

CREATE TABLE IF NOT EXISTS relations (
    id SERIAL PRIMARY KEY,
    document_id INTEGER NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    head_entity_id INTEGER NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    tail_entity_id INTEGER NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    relation_type VARCHAR(50) NOT NULL,
    confidence FLOAT,
    source VARCHAR(20) DEFAULT 'model',
    annotated_by INTEGER REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_relations_document ON relations(document_id);
CREATE INDEX idx_relations_type ON relations(relation_type);

CREATE TABLE IF NOT EXISTS timelines (
    id SERIAL PRIMARY KEY,
    document_id INTEGER NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    time_expression TEXT NOT NULL,
    normalized_date DATE,
    normalized_datetime TIMESTAMP,
    associated_event TEXT,
    entity_id INTEGER REFERENCES entities(id) ON DELETE SET NULL,
    confidence FLOAT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_timelines_document ON timelines(document_id);
CREATE INDEX idx_timelines_date ON timelines(normalized_date);

CREATE TABLE IF NOT EXISTS model_versions (
    id SERIAL PRIMARY KEY,
    version_name VARCHAR(50) UNIQUE NOT NULL,
    model_type VARCHAR(50) NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT FALSE,
    file_path VARCHAR(255),
    metrics JSONB,
    uploaded_by INTEGER REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS batch_tasks (
    id SERIAL PRIMARY KEY,
    task_name VARCHAR(255),
    status VARCHAR(20) DEFAULT 'pending',
    total_count INTEGER DEFAULT 0,
    processed_count INTEGER DEFAULT 0,
    failed_count INTEGER DEFAULT 0,
    input_file_path VARCHAR(255),
    output_file_path VARCHAR(255),
    model_version VARCHAR(50),
    submitted_by INTEGER REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT
);

CREATE INDEX idx_batch_tasks_status ON batch_tasks(status);

CREATE TABLE IF NOT EXISTS annotation_tasks (
    id SERIAL PRIMARY KEY,
    task_name VARCHAR(255),
    description TEXT,
    status VARCHAR(20) DEFAULT 'pending',
    total_documents INTEGER DEFAULT 0,
    assigned_to INTEGER REFERENCES users(id),
    created_by INTEGER REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deadline DATE
);

CREATE TABLE IF NOT EXISTS annotation_task_documents (
    id SERIAL PRIMARY KEY,
    annotation_task_id INTEGER NOT NULL REFERENCES annotation_tasks(id) ON DELETE CASCADE,
    document_id INTEGER NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    status VARCHAR(20) DEFAULT 'pending',
    annotated_at TIMESTAMP,
    UNIQUE(annotation_task_id, document_id)
);

CREATE TABLE IF NOT EXISTS evaluation_datasets (
    id SERIAL PRIMARY KEY,
    dataset_name VARCHAR(255) UNIQUE NOT NULL,
    description TEXT,
    document_count INTEGER DEFAULT 0,
    file_path VARCHAR(255),
    created_by INTEGER REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS evaluation_results (
    id SERIAL PRIMARY KEY,
    model_version_id INTEGER NOT NULL REFERENCES model_versions(id) ON DELETE CASCADE,
    dataset_id INTEGER NOT NULL REFERENCES evaluation_datasets(id) ON DELETE CASCADE,
    entity_type VARCHAR(50),
    precision FLOAT,
    recall FLOAT,
    f1_score FLOAT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(model_version_id, dataset_id, entity_type)
);
