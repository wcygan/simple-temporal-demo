-- Create the content table with comprehensive fields for content management (MySQL version)
CREATE TABLE content (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    author_id VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    tags JSON DEFAULT (JSON_ARRAY()),
    temporal_workflow_id VARCHAR(255),
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Create indexes for performance
CREATE INDEX idx_content_status ON content(status);
CREATE INDEX idx_content_author ON content(author_id);
CREATE INDEX idx_content_workflow ON content(temporal_workflow_id);
CREATE INDEX idx_content_created_date ON content(created_date);

-- Create full-text search index for title and content
CREATE FULLTEXT INDEX idx_content_fts ON content(title, content);

-- Insert some sample data for testing
INSERT INTO content (title, content, author_id, status, tags) VALUES
('Getting Started with Quarkus', 'Quarkus is a Kubernetes Native Java stack tailored for OpenJDK HotSpot and GraalVM...', 'author1', 'DRAFT', JSON_ARRAY('java', 'quarkus', 'tutorial')),
('Introduction to Temporal', 'Temporal is a developer-first platform for building resilient applications...', 'author2', 'UNDER_REVIEW', JSON_ARRAY('temporal', 'workflow', 'orchestration')),
('MySQL Performance Tips', 'Here are some proven techniques to optimize MySQL performance...', 'author1', 'PUBLISHED', JSON_ARRAY('mysql', 'database', 'performance'));