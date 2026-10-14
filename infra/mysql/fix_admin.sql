USE agent_memory;
UPDATE app_user SET password_change_required=0 WHERE username='admin';
SELECT username, password_change_required FROM app_user WHERE username='admin';
