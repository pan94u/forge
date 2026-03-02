-- =============================================================================
-- V21: Create Skill Marketplace tables
-- Supports skill publishing, reviews/ratings, and quality management
-- =============================================================================

-- Marketplace listings: published skills visible to all users
CREATE TABLE marketplace_listings (
    id VARCHAR(36) PRIMARY KEY,
    skill_name VARCHAR(255) NOT NULL,
    author_id VARCHAR(255) NOT NULL,
    author_name VARCHAR(255) NOT NULL DEFAULT '',
    description TEXT,
    tags TEXT,                               -- comma-separated tags
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, SUSPENDED, STALE
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    publish_reason VARCHAR(500),
    suspend_reason VARCHAR(500),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_marketplace_skill_name UNIQUE (skill_name)
);

CREATE INDEX idx_mpl_status ON marketplace_listings(status);
CREATE INDEX idx_mpl_author_id ON marketplace_listings(author_id);
CREATE INDEX idx_mpl_featured ON marketplace_listings(featured);
CREATE INDEX idx_mpl_created_at ON marketplace_listings(created_at);

-- Skill reviews: user ratings and comments
CREATE TABLE skill_reviews (
    id VARCHAR(36) PRIMARY KEY,
    listing_id VARCHAR(36) NOT NULL,
    skill_name VARCHAR(255) NOT NULL,
    reviewer_id VARCHAR(255) NOT NULL,
    reviewer_name VARCHAR(255) NOT NULL DEFAULT '',
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_review_listing FOREIGN KEY (listing_id) REFERENCES marketplace_listings(id) ON DELETE CASCADE,
    CONSTRAINT uq_review_per_user UNIQUE (listing_id, reviewer_id)
);

CREATE INDEX idx_sr_listing_id ON skill_reviews(listing_id);
CREATE INDEX idx_sr_skill_name ON skill_reviews(skill_name);
CREATE INDEX idx_sr_reviewer_id ON skill_reviews(reviewer_id);
