-- banking_core_service.banking_core_member definition

CREATE TABLE `banking_core_member` (
    `id`                    bigint(20) NOT NULL AUTO_INCREMENT,
    `first_name`            varchar(255) DEFAULT NULL,
    `last_name`             varchar(255) DEFAULT NULL,
    `email`                 varchar(255) DEFAULT NULL,
    `phone_number`          varchar(50)  DEFAULT NULL,
    `identification_number` varchar(255) DEFAULT NULL,
    `membership_type`       varchar(50)  DEFAULT NULL,
    `address`               varchar(500) DEFAULT NULL,
    `date_of_birth`         date         DEFAULT NULL,
    `status`                varchar(50)  DEFAULT 'ACTIVE',
    `created_at`            datetime     DEFAULT NULL,
    `updated_at`            datetime     DEFAULT NULL,
    PRIMARY KEY (`id`)
);
