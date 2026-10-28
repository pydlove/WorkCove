CREATE DATABASE IF NOT EXISTS `workcove_client`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

GRANT ALL PRIVILEGES ON `workcove_client`.*
    TO 'workcove_app'@'127.0.0.1';

FLUSH PRIVILEGES;
