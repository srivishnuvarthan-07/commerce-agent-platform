CREATE TABLE demo_persona (
    actor_user_id VARCHAR(64) NOT NULL,
    category VARCHAR(32) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    job_title VARCHAR(100) NOT NULL,
    department VARCHAR(100) NOT NULL,
    description VARCHAR(255) NOT NULL,
    welcome_message VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (actor_user_id),
    INDEX idx_demo_persona_category_sort (category, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE demo_persona_rag_scope (
    actor_user_id VARCHAR(64) NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    scope_value VARCHAR(100) NOT NULL,
    PRIMARY KEY (actor_user_id, scope_type, scope_value),
    CONSTRAINT fk_demo_scope_persona FOREIGN KEY (actor_user_id)
        REFERENCES demo_persona(actor_user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE demo_persona_capability (
    actor_user_id VARCHAR(64) NOT NULL,
    capability VARCHAR(64) NOT NULL,
    PRIMARY KEY (actor_user_id, capability),
    CONSTRAINT fk_demo_capability_persona FOREIGN KEY (actor_user_id)
        REFERENCES demo_persona(actor_user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE demo_persona_suggestion (
    actor_user_id VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL,
    suggestion VARCHAR(255) NOT NULL,
    PRIMARY KEY (actor_user_id, sort_order),
    CONSTRAINT fk_demo_suggestion_persona FOREIGN KEY (actor_user_id)
        REFERENCES demo_persona(actor_user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sales_customer_assignment (
    sales_actor_user_id VARCHAR(64) NOT NULL,
    customer_user_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (sales_actor_user_id, customer_user_id),
    CONSTRAINT fk_sales_assignment_persona FOREIGN KEY (sales_actor_user_id)
        REFERENCES demo_persona(actor_user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO demo_persona
    (actor_user_id, category, display_name, job_title, department, description, welcome_message, sort_order)
VALUES
    ('HR001', 'HR', 'Lin Yue', 'HRBP', 'HumanResources', 'Handles employee policies, benefits, and organizational collaboration', 'Hi, I am Lin Yue. I can demo employee policies, benefits, and HR collaboration scenarios.', 10),
    ('HR002', 'HR', 'Chen Chen', 'Recruiter', 'HumanResources', 'Handles recruitment processes and new employee onboarding', 'Hi, I am Chen Chen. I can demo recruitment, onboarding, and probation-related Q&A.', 20),
    ('DEV001', 'ENGINEERING', 'Zhou Hang', 'Backend Engineer', 'Engineering', 'Focuses on engineering standards, APIs, and code quality', 'Hi, I am Zhou Hang. I can demo technical development standards and order system engineering knowledge.', 30),
    ('DEV002', 'ENGINEERING', 'Zhao Ning', 'Platform Engineer', 'Platform', 'Responsible for Agent platform operations and troubleshooting', 'Hi, I am Zhao Ning. I can demo development standards, Agent operations, and platform troubleshooting.', 40),
    ('SALES001', 'SALES', 'Wang Lei', 'East China Sales', 'Sales', 'Handles East China customer follow-up and order coordination', 'Hi, I am Wang Lei. I can demo sales rules and order tracking for my assigned customers.', 50),
    ('SALES002', 'SALES', 'Liu Ting', 'Key Account Sales', 'Sales', 'Handles key account management and order coordination', 'Hi, I am Liu Ting. I can demo key account sales rules and order tracking.', 60),
    ('USER1001', 'CUSTOMER', 'Zhang Wei', 'Individual Customer', 'Customer', 'Has two active orders, good for demonstrating refund and logistics scenarios', 'Hi, I am Zhang Wei. I can view my orders, track shipments, and initiate after-sales requests.', 70),
    ('USER1002', 'CUSTOMER', 'Li Na', 'Individual Customer', 'Customer', 'Has one completed order, good for demonstrating historical order scenarios', 'Hi, I am Li Na. I can view my completed orders and learn about after-sales policies.', 80);

INSERT INTO demo_persona_rag_scope (actor_user_id, scope_type, scope_value) VALUES
    ('HR001', 'ROLE', 'public'), ('HR001', 'ROLE', 'hr'),
    ('HR002', 'ROLE', 'public'), ('HR002', 'ROLE', 'hr'),
    ('DEV001', 'ROLE', 'public'), ('DEV001', 'ROLE', 'developer'),
    ('DEV002', 'ROLE', 'public'), ('DEV002', 'ROLE', 'developer'), ('DEV002', 'ROLE', 'admin'),
    ('SALES001', 'ROLE', 'public'), ('SALES001', 'ROLE', 'sales'),
    ('SALES002', 'ROLE', 'public'), ('SALES002', 'ROLE', 'sales'),
    ('USER1001', 'ROLE', 'public'), ('USER1001', 'ROLE', 'customer_service'),
    ('USER1002', 'ROLE', 'public'), ('USER1002', 'ROLE', 'customer_service');

INSERT INTO demo_persona_capability (actor_user_id, capability) VALUES
    ('HR001', 'KNOWLEDGE_SEARCH'), ('HR002', 'KNOWLEDGE_SEARCH'),
    ('DEV001', 'KNOWLEDGE_SEARCH'), ('DEV002', 'KNOWLEDGE_SEARCH'),
    ('SALES001', 'KNOWLEDGE_SEARCH'), ('SALES001', 'ASSIGNED_ORDER_READ'),
    ('SALES002', 'KNOWLEDGE_SEARCH'), ('SALES002', 'ASSIGNED_ORDER_READ'),
    ('USER1001', 'KNOWLEDGE_SEARCH'), ('USER1001', 'OWN_ORDER_READ'),
    ('USER1001', 'ORDER_CANCEL'), ('USER1001', 'AFTER_SALES_CREATE'),
    ('USER1002', 'KNOWLEDGE_SEARCH'), ('USER1002', 'OWN_ORDER_READ'),
    ('USER1002', 'ORDER_CANCEL'), ('USER1002', 'AFTER_SALES_CREATE');

INSERT INTO demo_persona_suggestion (actor_user_id, sort_order, suggestion) VALUES
    ('HR001', 1, 'How is annual leave calculated?'), ('HR001', 2, 'What employee benefits does the company offer?'), ('HR001', 3, 'What are the cross-department collaboration guidelines?'),
    ('HR002', 1, 'What does a new employee need to prepare for onboarding?'), ('HR002', 2, 'What are the probation period management requirements?'), ('HR002', 3, 'What is the recruitment interview process?'),
    ('DEV001', 1, 'What standards apply to order API development?'), ('DEV001', 2, 'What are the code review requirements?'), ('DEV001', 3, 'How should API idempotency be handled?'),
    ('DEV002', 1, 'How do I troubleshoot Agent service errors?'), ('DEV002', 2, 'What should I do if knowledge base import fails?'), ('DEV002', 3, 'What should be checked before a service goes live?'),
    ('SALES001', 1, 'What should I watch for when following up on orders?'), ('SALES001', 2, 'View orders for my assigned customers'), ('SALES001', 3, 'How do I coordinate a customer refund request?'),
    ('SALES002', 1, 'How do I follow up on key account orders?'), ('SALES002', 2, 'View orders for my assigned customers'), ('SALES002', 3, 'What are the boundaries for sales pricing?'),
    ('USER1001', 1, 'View my orders'), ('USER1001', 2, 'Can I get a refund for ORD20260810001?'), ('USER1001', 3, 'When will my order be shipped?'),
    ('USER1002', 1, 'View my orders'), ('USER1002', 2, 'Can I still request after-sales for a completed order?'), ('USER1002', 3, 'Explain the after-sales processing rules');

INSERT INTO sales_customer_assignment (sales_actor_user_id, customer_user_id) VALUES
    ('SALES001', 'USER1001'),
    ('SALES002', 'USER1002');
