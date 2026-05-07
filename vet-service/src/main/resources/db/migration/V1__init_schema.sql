CREATE TABLE specialties (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(80)  NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE vets (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    first_name VARCHAR(30)  NOT NULL,
    last_name  VARCHAR(30)  NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE vet_specialties (
    vet_id       INT NOT NULL,
    specialty_id INT NOT NULL,
    PRIMARY KEY (vet_id, specialty_id),
    CONSTRAINT fk_vet_specialties_vet FOREIGN KEY (vet_id) REFERENCES vets (id),
    CONSTRAINT fk_vet_specialties_specialty FOREIGN KEY (specialty_id) REFERENCES specialties (id)
);

CREATE INDEX idx_vets_last_name ON vets (last_name);
CREATE INDEX idx_specialties_name ON specialties (name);
