CREATE TABLE specialties (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(80)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE vets (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    first_name  VARCHAR(30)  NOT NULL,
    last_name   VARCHAR(30)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE vet_specialties (
    vet_id       INT NOT NULL,
    specialty_id INT NOT NULL,
    PRIMARY KEY (vet_id, specialty_id),
    CONSTRAINT fk_vet_specialties_vet FOREIGN KEY (vet_id) REFERENCES vets (id) ON DELETE CASCADE,
    CONSTRAINT fk_vet_specialties_specialty FOREIGN KEY (specialty_id) REFERENCES specialties (id) ON DELETE CASCADE
);

INSERT INTO specialties (id, name) VALUES (1, 'radiology');
INSERT INTO specialties (id, name) VALUES (2, 'surgery');
INSERT INTO specialties (id, name) VALUES (3, 'dentistry');

INSERT INTO vets (id, first_name, last_name) VALUES (1, 'James', 'Carter');
INSERT INTO vets (id, first_name, last_name) VALUES (2, 'Helen', 'Leary');
INSERT INTO vets (id, first_name, last_name) VALUES (3, 'Linda', 'Douglas');

INSERT INTO vet_specialties (vet_id, specialty_id) VALUES (2, 1);
INSERT INTO vet_specialties (vet_id, specialty_id) VALUES (3, 2);
INSERT INTO vet_specialties (vet_id, specialty_id) VALUES (3, 3);

ALTER TABLE specialties ALTER COLUMN id RESTART WITH 100;
ALTER TABLE vets ALTER COLUMN id RESTART WITH 100;
