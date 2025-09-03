-- Clean CREATE schema (MySQL 8+)
DROP TRIGGER IF EXISTS biu_empleados_tag_guard;
DROP TRIGGER IF EXISTS bu_empleados_tag_guard;
DROP TRIGGER IF EXISTS biu_equipment_tag_guard;
DROP TRIGGER IF EXISTS bu_equipment_tag_guard;

DROP TABLE IF EXISTS empleados;
DROP TABLE IF EXISTS equipment;
DROP TABLE IF EXISTS tags_uhf;
DROP TABLE IF EXISTS ubicaciones;
DROP TABLE IF EXISTS categorias;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS roles;
DROP TABLE IF EXISTS permissions;
DROP TABLE IF EXISTS roles_permissions;
DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS detecciones_tags;

CREATE TABLE roles (id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(50) NOT NULL UNIQUE);
CREATE TABLE permissions (id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(80) NOT NULL UNIQUE);
CREATE TABLE roles_permissions (role_id BIGINT NOT NULL, permission_id BIGINT NOT NULL, PRIMARY KEY (role_id, permission_id));
CREATE TABLE users (id BIGINT PRIMARY KEY AUTO_INCREMENT, username VARCHAR(60) NOT NULL UNIQUE, password VARCHAR(120) NOT NULL, full_name VARCHAR(100) NOT NULL, system_user BOOLEAN NOT NULL DEFAULT 0);
CREATE TABLE user_roles (user_id BIGINT NOT NULL, role_id BIGINT NOT NULL, PRIMARY KEY (user_id, role_id));

CREATE TABLE categorias (id BIGINT PRIMARY KEY AUTO_INCREMENT, nombre VARCHAR(255) NOT NULL);
CREATE TABLE ubicaciones (id BIGINT PRIMARY KEY AUTO_INCREMENT, nombre VARCHAR(150) NOT NULL UNIQUE);

CREATE TABLE tags_uhf (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  epc VARCHAR(64) NOT NULL,
  tipo ENUM('EMPLEADO','EQUIPMENT') NOT NULL,
  activo BOOLEAN NOT NULL DEFAULT 1,
  CONSTRAINT uk_taguhf_epc UNIQUE KEY (epc)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE empleados (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  full_name  VARCHAR(150) NOT NULL,
  last_name  VARCHAR(150) NOT NULL,
  doc_type   VARCHAR(20)  NOT NULL,
  doc_number VARCHAR(30)  NOT NULL,
  birth_date DATE         NOT NULL,
  blood_type VARCHAR(4)   NOT NULL,
  email      VARCHAR(120),
  phone      VARCHAR(25),
  tag_id     BIGINT NULL,
  CONSTRAINT uk_empleados_doc_number UNIQUE KEY (doc_number),
  CONSTRAINT uk_empleados_tag UNIQUE KEY (tag_id),
  CONSTRAINT fk_empleados_tag FOREIGN KEY (tag_id) REFERENCES tags_uhf(id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE equipment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  sku     VARCHAR(64),
  nombre  VARCHAR(150),
  serie   VARCHAR(100),
  estado  VARCHAR(30),
  tag_id  BIGINT NULL,
  CONSTRAINT uk_equipment_tag UNIQUE KEY (tag_id),
  CONSTRAINT fk_equipment_tag FOREIGN KEY (tag_id) REFERENCES tags_uhf(id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE detecciones_tags (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  lector_id     BIGINT NOT NULL,
  ubicacion_id  BIGINT NULL,
  epc           VARCHAR(64) NOT NULL,
  rssi          INT NULL,
  machine       VARCHAR(100) NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_detecciones_lector
    FOREIGN KEY (lector_id) REFERENCES lectores_uhf(id) ON DELETE RESTRICT,
  CONSTRAINT fk_detecciones_ubicacion
    FOREIGN KEY (ubicacion_id) REFERENCES ubicaciones(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_detecciones_epc              ON detecciones_tags (epc);
CREATE INDEX idx_detecciones_created          ON detecciones_tags (created_at);
CREATE INDEX idx_detecciones_lector_created   ON detecciones_tags (lector_id, created_at);
CREATE INDEX idx_detecciones_epc_created
    ON detecciones_tags (epc, created_at);

CREATE INDEX idx_detecciones_ubicacion_created
    ON detecciones_tags (ubicacion_id, created_at);

DELIMITER $$

CREATE TRIGGER biu_empleados_tag_guard
BEFORE INSERT ON empleados
FOR EACH ROW
BEGIN
  IF NEW.tag_id IS NOT NULL AND EXISTS (SELECT 1 FROM equipment WHERE tag_id = NEW.tag_id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Tag already assigned to an equipment';
  END IF;
END$$

CREATE TRIGGER bu_empleados_tag_guard
BEFORE UPDATE ON empleados
FOR EACH ROW
BEGIN
  IF NEW.tag_id IS NOT NULL AND NEW.tag_id <> OLD.tag_id
     AND EXISTS (SELECT 1 FROM equipment WHERE tag_id = NEW.tag_id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Tag already assigned to an equipment';
  END IF;
END$$

CREATE TRIGGER biu_equipment_tag_guard
BEFORE INSERT ON equipment
FOR EACH ROW
BEGIN
  IF NEW.tag_id IS NOT NULL AND EXISTS (SELECT 1 FROM empleados WHERE tag_id = NEW.tag_id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Tag already assigned to an empleado';
  END IF;
END$$

CREATE TRIGGER bu_equipment_tag_guard
BEFORE UPDATE ON equipment
FOR EACH ROW
BEGIN
  IF NEW.tag_id IS NOT NULL AND NEW.tag_id <> OLD.tag_id
     AND EXISTS (SELECT 1 FROM empleados WHERE tag_id = NEW.tag_id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Tag already assigned to an empleado';
  END IF;
END$$

DELIMITER ;