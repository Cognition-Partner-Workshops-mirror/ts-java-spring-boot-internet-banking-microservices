CREATE USER 'javatodev_development'@'%' IDENTIFIED BY 'oPItyPticIAt';
GRANT CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES on *.* TO 'javatodev_development'@'%';
FLUSH PRIVILEGES;

CREATE DATABASE IF NOT EXISTS banking_core_service;
CREATE DATABASE IF NOT EXISTS banking_core_fund_transfer_service;
CREATE DATABASE IF NOT EXISTS banking_core_user_service;
CREATE DATABASE IF NOT EXISTS banking_core_utility_payment_service;

-- New database for the AI Agent Service (Client Health, RM Copilot, Wallet Share agents)
CREATE DATABASE IF NOT EXISTS banking_ai_agent_service;
