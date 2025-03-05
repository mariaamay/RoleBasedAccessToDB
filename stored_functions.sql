CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(10) CHECK (role IN ('admin', 'guest')) NOT NULL
);

-- INSERT INTO users (username, password, role) VALUES
-- ('admin', 'admin_password', 'admin'),
-- ('guest', 'guest_password', 'guest')

-- Для удаленной работы с базой данных training_schedule
DO $$
BEGIN
  IF NOT EXISTS (select 1 from pg_extension where extname = 'dblink') then
    CREATE EXTENSION dblink;
  END IF;
END$$;

--1. Создание базы данных
CREATE OR REPLACE FUNCTION create_database(db_name TEXT)
RETURNS void 
AS $$
DECLARE
  connection_string TEXT;
BEGIN
  IF NOT EXISTS (select 1 from pg_database where datname = db_name) then
    connection_string := 'dbname=trainings user=postgres password=CHocolate75% host=localhost';
    PERFORM dblink_connect(connection_string);
    PERFORM dblink_exec('create database ' || db_name);
    PERFORM dblink_disconnect();
  END IF;
END;
$$LANGUAGE plpgsql;

-- 2. Удаление базы данных
CREATE OR REPLACE FUNCTION drop_database(db_name TEXT, out result INT)
AS $$
DECLARE
  connection_string TEXT;
  r RECORD;
BEGIN
	-- Проверяем, существует ли база данных
	IF NOT EXISTS (select 1 from pg_database where datname = db_name) then
		raise notice 'Ошибка: база данных % не существует.', db_name;
		result := -2; -- Код ошибки для отсутствующей базы данных
		return;
	end if;
  
  	 -- Завершаем соединения с базой данных (выполняем локально)
    for r in (select pid from pg_stat_activity where datname = db_name) loop
        execute format('SELECT pg_terminate_backend(%s)', r.pid);
    end loop;
		
	-- Удаляем базу данных
    connection_string := 'dbname=trainings user=postgres password=CHocolate75% host=localhost';
    perform dblink_connect(connection_string);
    perform dblink_exec('drop database ' || db_name);
	PERFORM dblink_exec('TRUNCATE TABLE trainings RESTART IDENTITY');
    perform dblink_disconnect();

	raise notice 'База данных % успешно удалена!', db_name;
    result := 1;
end;
$$language plpgsql;

-- 3. Создание таблицы
CREATE OR REPLACE FUNCTION create_table(db_name text, out table_created int)
as $$
declare 
	db_exists boolean;
	table_exists boolean;
begin
	-- Проверяем, существует ли база данных через dblink
    SELECT EXISTS (
        SELECT 1 FROM dblink('dbname=trainings user=postgres password=CHocolate75%',
                             'SELECT 1 FROM pg_database WHERE datname = ''' || db_name || '''')
        as t(exists boolean)
    ) into db_exists;

	IF NOT db_exists THEN
	    RAISE NOTICE 'Ошибка: база данных % не существует.', db_name;
	    table_created := -2; -- Код ошибки для отсутствующей базы данных
		RETURN;
	END IF;

	-- Проверяем, существует ли таблица trainings в training_schedule
    SELECT EXISTS (
        SELECT 1 FROM dblink('dbname=training_schedule user=postgres password=CHocolate75%',
                             'SELECT 1 FROM information_schema.tables WHERE table_schema = ''public'' AND table_name = ''trainings''')
        AS t(exists BOOLEAN)
    ) INTO table_exists;

    
    IF table_exists THEN
        RAISE NOTICE 'Ошибка: таблица trainings уже существует.';
        table_created := -1; -- Код ошибки для отсутствующей таблицы
        RETURN;
    END IF;

    -- Создаём таблицу в training_schedule через dblink
    PERFORM dblink_exec('dbname=training_schedule user=postgres password=CHocolate75%',
        'CREATE TABLE trainings (
            class_id SERIAL PRIMARY KEY,
            title VARCHAR(255) NOT NULL,
            date DATE NOT NULL,
            start_time TIME NOT NULL,
            duration INTERVAL NOT NULL,
            max_participants INT CHECK (max_participants > 0),
            current_participants INT CHECK (current_participants >= 0),
            difficulty_level VARCHAR(20) CHECK (difficulty_level IN (''начальный'', ''смешанный'', ''продвинутый'')),
            trainer_name VARCHAR(255) NOT NULL,
            price DECIMAL(10, 2)
        )');

    RAISE NOTICE 'Таблица trainings создана!';
    table_created := 1;
END;
$$ LANGUAGE plpgsql;

-- 4. Очистка базы данных
CREATE OR REPLACE FUNCTION clear_database(db_name TEXT)
RETURNS INT 
AS $$
DECLARE 
    db_exists BOOLEAN;
    table_exists BOOLEAN;
    connection_string TEXT;
    deleted_rows INT;
BEGIN
    -- Проверяем, существует ли база данных через dblink
    SELECT EXISTS (
        SELECT 1 FROM dblink('dbname=trainings user=postgres password=CHocolate75% host=localhost',
                             'SELECT 1 FROM pg_database WHERE datname = ''' || db_name || '''')
        AS t(exists BOOLEAN)
    ) INTO db_exists;

    IF NOT db_exists THEN
        RAISE NOTICE 'Ошибка: база данных "%" не существует.', db_name;
        RETURN -2; -- Код ошибки для отсутствующей базы данных
    END IF;

    connection_string := 'dbname=trainings user=postgres password=CHocolate75% host=localhost';
    perform dblink_connect('myconn', connection_string);

    -- Проверяем, существует ли таблица trainings
    SELECT EXISTS (
        SELECT 1 FROM dblink('myconn', 
                             'SELECT 1 FROM information_schema.tables WHERE table_schema = ''public'' AND table_name = ''trainings''')
        AS t(exists BOOLEAN)
    ) INTO table_exists;

    IF NOT table_exists THEN
        RAISE NOTICE 'Ошибка: таблица "trainings" не существует в базе данных "%".', db_name;
        PERFORM dblink_disconnect('myconn');
        RETURN -1; -- Код ошибки для отсутствующей таблицы
    END IF;

    -- Удаляем строки и получаем количество удаленных
    SELECT COALESCE(SUM(deleted), 0) INTO deleted_rows FROM dblink('myconn', 
        'WITH deleted AS (DELETE FROM trainings RETURNING 1) SELECT COUNT(*) FROM deleted'
    ) AS t(deleted INT);
	
    PERFORM dblink_exec('myconn', 'DELETE FROM trainings');
    PERFORM dblink_exec('myconn', 'TRUNCATE TABLE trainings RESTART IDENTITY');

    -- Отключаемся от базы данных
    PERFORM dblink_disconnect('myconn');

    RAISE NOTICE 'Удалено % строк из базы "%".', deleted_rows, db_name;
    RETURN deleted_rows; -- Возвращаем количество удаленных строк
END;
$$ LANGUAGE plpgsql;

-- 4. Добавление новых данных
CREATE OR REPLACE FUNCTION add_new_training(
    db_name TEXT,
    p_title VARCHAR(255),
    p_date DATE,
    p_start_time TIME,
    p_duration INTERVAL,
    p_max_participants INT,
    p_current_participants INT,
    p_difficulty_level VARCHAR(20),
    p_coach VARCHAR(255),
    p_price DECIMAL(10,2)
)
RETURNS INT
AS $$
DECLARE
    db_exists BOOLEAN;
    table_exists BOOLEAN;
    conflict_exists BOOLEAN;
    connection_string TEXT;
    new_training_id INT;
BEGIN
    -- Проверяем, существует ли база данных
    SELECT EXISTS (
        SELECT 1 FROM pg_database WHERE datname = db_name
    ) INTO db_exists;

    IF NOT db_exists THEN
        RAISE NOTICE 'Ошибка: база данных % не существует.', db_name;
        RETURN -2; -- Код ошибки для отсутствующей базы данных 
    END IF;
  
    connection_string := 'dbname=trainings user=postgres password=CHocolate75% host=localhost';
    PERFORM dblink_connect('myconn', connection_string);

    -- Проверяем, существует ли таблица trainings
    SELECT EXISTS (
        SELECT 1 FROM dblink('myconn', 
                             'SELECT 1 FROM information_schema.tables WHERE table_name = ''trainings''')
        AS t(exists BOOLEAN)
    ) INTO table_exists;

    IF NOT table_exists THEN
        RAISE NOTICE 'Ошибка: таблица trainings не существует в базе %.', db_name;
        PERFORM dblink_disconnect('myconn');
        RETURN -1; -- Код ошибки для отсутствующей таблицы
    END IF;

    -- Проверка входных данных
    IF p_max_participants <= 0 THEN
        RAISE NOTICE 'Ошибка: Максимальное количество участников должно быть положительным.';
        PERFORM dblink_disconnect('myconn');
        RETURN -3;
    END IF;

    IF p_current_participants < 0 THEN
        RAISE NOTICE 'Ошибка: Текущее количество участников должно быть неотрицательным.';
        PERFORM dblink_disconnect('myconn');
        RETURN -4;
    END IF;

    IF p_difficulty_level NOT IN ('начальный', 'смешанный', 'продвинутый') THEN
        RAISE NOTICE 'Ошибка: Некорректный уровень сложности.';
        PERFORM dblink_disconnect('myconn');
        RETURN -5;
    END IF;

    -- Проверяем пересечение времени тренировок у тренера
    SELECT EXISTS (
        SELECT 1 FROM dblink('myconn', 
            'SELECT 1 FROM trainings WHERE date = ''' || p_date || ''' 
             AND trainer_name = ''' || p_coach || ''' 
             AND (''' || p_start_time || '''::time, (''' || p_start_time || '''::time + ''' || p_duration || '''::interval)) 
             OVERLAPS (start_time, start_time + duration)')
        AS t(exists BOOLEAN)
    ) INTO conflict_exists;

    IF conflict_exists THEN 
        RAISE NOTICE 'Ошибка: Время занятия у тренера % пересекается с другим занятием.', p_coach;
        PERFORM dblink_disconnect('myconn');
        RETURN -6;
    END IF;

    -- Выполняем INSERT через dblink
    SELECT class_id FROM dblink('myconn', 
        'INSERT INTO trainings (title, date, start_time, duration, max_participants, 
                                current_participants, difficulty_level, trainer_name, price) 
         VALUES (''' || p_title || ''', ''' || p_date || ''', ''' || p_start_time || ''', ''' || p_duration || ''', 
                 ' || p_max_participants || ', ' || p_current_participants || ', ''' || p_difficulty_level || ''', 
                 ''' || p_coach || ''', ' || p_price || ') RETURNING class_id')
    AS t(class_id INT) INTO new_training_id;
    PERFORM dblink_disconnect('myconn');

    IF new_training_id IS NOT NULL THEN
        RAISE NOTICE 'Тренировка успешно добавлена с ID %', new_training_id;
        RETURN new_training_id;
    ELSE
        RAISE NOTICE 'Ошибка при добавлении тренировки.';
        RETURN -7;
    END IF;
END;
$$ LANGUAGE plpgsql;


-- 5. Поиск по заранее выбранному (одному) текстовому не ключевому полю в базе данных
CREATE OR REPLACE FUNCTION search_training_by_field(
	db_name TEXT,
	field_name TEXT,
	search_value TEXT
)
RETURNS TABLE(
	id INT,
    title VARCHAR(255),
    date DATE,
    start_time TIME,
    duration INTERVAL,
    max_participants INT,
    current_participants INT,
    difficulty_level VARCHAR(20),
    trainer_name VARCHAR(255),
    price DECIMAL(10,2)
)
AS $$ 
DECLARE
    db_exists BOOLEAN;
    table_exists BOOLEAN;
    field_is_valid BOOLEAN;
    connection_string TEXT;
    sql_query TEXT;
BEGIN
    -- Проверяем существование базы данных через dblink
    SELECT EXISTS (
        SELECT 1 FROM dblink('dbname=trainings user=postgres password=CHocolate75% host=localhost',
                             'SELECT 1 FROM pg_database WHERE datname = ''' || db_name || '''')
        AS t(exists BOOLEAN)
    ) INTO db_exists;

    IF NOT db_exists THEN
        RAISE NOTICE 'Ошибка: база данных "%" не существует.', db_name; 
        RETURN;
    END IF;

     connection_string := 'dbname=trainings user=postgres password=CHocolate75% host=localhost';
    PERFORM dblink_connect('myconn', connection_string);

    -- Проверяем, существует ли таблица trainings
    SELECT EXISTS (
        SELECT 1 FROM dblink('myconn', 
                             'SELECT 1 FROM information_schema.tables WHERE table_schema = ''public'' AND table_name = ''trainings''')
        AS t(exists BOOLEAN)
    ) INTO table_exists;

    IF NOT table_exists THEN
        RAISE NOTICE 'Ошибка: таблица trainings не существует в базе "%".', db_name;
        PERFORM dblink_disconnect('myconn');
        RETURN;
    END IF;

    -- Проверяем, что поле существует и является текстовым
    SELECT EXISTS (
        SELECT 1 FROM dblink('myconn', 
            'SELECT 1 FROM information_schema.columns WHERE table_name = ''trainings'' 
             AND column_name = ''' || field_name || ''' 
             AND data_type IN (''character varying'', ''text'')')
        AS t(exists BOOLEAN)
    ) INTO field_is_valid;

    IF NOT field_is_valid THEN
        RAISE NOTICE 'Ошибка: поле "%" не существует или не является текстовым.', field_name;
        PERFORM dblink_disconnect('myconn');
        RETURN;
    END IF;

    -- Проверяем, что значение для поиска не пустое
    IF search_value IS NULL OR TRIM(search_value) = '' THEN
        RAISE NOTICE 'Ошибка: значение для поиска не может быть пустым.';
        PERFORM dblink_disconnect('myconn');
        RETURN;
    END IF;

    -- Динамически формируем SQL-запрос
    sql_query := format(
        'SELECT * FROM trainings WHERE %I ILIKE %L',
        field_name, '%' || search_value || '%'
    );

    -- Выполняем запрос через dblink
    RETURN QUERY 
    SELECT * FROM dblink('myconn', sql_query) 
        AS t(
            id INT,
            title VARCHAR(255),
            date DATE,
            start_time TIME,
            duration INTERVAL,
            max_participants INT,
            current_participants INT,
            difficulty_level VARCHAR(20),
            trainer_name VARCHAR(255),
            price DECIMAL(10,2)
        );

    PERFORM dblink_disconnect('myconn');
END;
$$ LANGUAGE plpgsql;

--6. Удаление по заранее выбранному (одному) текстовому не ключевому полю в базе данных
CREATE OR REPLACE FUNCTION delete_trainings_by_field(
    db_name TEXT,
    field_name TEXT,
    search_value TEXT
)
RETURNS INT
AS $$ 
DECLARE
    db_exists BOOLEAN;
    table_exists BOOLEAN;
    field_exists BOOLEAN;
    rows_deleted INT := 0;
    connection_string TEXT;
    sql_query TEXT;
BEGIN
    -- Проверяем существование базы данных через dblink
    SELECT EXISTS (
        SELECT 1 FROM dblink('dbname=trainings user=postgres password=CHocolate75% host=localhost',
                             'SELECT 1 FROM pg_database WHERE datname = ''' || db_name || '''')
        AS t(exists BOOLEAN)
    ) INTO db_exists;

    IF NOT db_exists THEN
        RAISE NOTICE 'Ошибка: база данных "%" не существует.', db_name;
        RETURN -2; -- Код ошибки для отсутствующей базы данных
    END IF;

    connection_string := 'dbname=trainings user=postgres password=CHocolate75% host=localhost';
    PERFORM dblink_connect('myconn', connection_string);

    -- Проверяем, существует ли таблица trainings
    SELECT EXISTS (
        SELECT 1 FROM dblink('myconn', 
                             'SELECT 1 FROM information_schema.tables WHERE table_schema = ''public'' AND table_name = ''trainings''')
        AS t(exists BOOLEAN)
    ) INTO table_exists;

    IF NOT table_exists THEN
        RAISE NOTICE 'Ошибка: таблица "trainings" не существует в базе "%".', db_name;
        PERFORM dblink_disconnect('myconn');
        RETURN -1; -- Код ошибки для отсутствующей таблицы
    END IF;

    -- Проверяем существование указанного текстового столбца
    SELECT EXISTS (
        SELECT 1 FROM dblink('myconn',
            'SELECT 1 FROM information_schema.columns WHERE table_name = ''trainings'' 
             AND column_name = ''' || field_name || ''' 
             AND data_type IN (''character varying'', ''text'')')
        AS t(exists BOOLEAN)
    ) INTO field_exists;

    IF NOT field_exists THEN
        RAISE NOTICE 'Ошибка: Поле "%" не существует или не является текстовым.', field_name;
        PERFORM dblink_disconnect('myconn');
        RETURN -7; -- Код ошибки для некорректного поля для удаления
    END IF;

    -- Проверяем, что передано корректное значение для удаления
    IF search_value IS NULL OR TRIM(search_value) = '' THEN
        RAISE NOTICE 'Ошибка: Значение для удаления не может быть пустым.';
        PERFORM dblink_disconnect('myconn');
        RETURN -8; -- Код ошибки для пустого значения
    END IF;

    -- Определяем количество записей, которые будут удалены
    SELECT count FROM dblink('myconn',
        format('SELECT COUNT(*) FROM trainings WHERE %I ILIKE %L', field_name, '%' || search_value || '%'))
        AS t(count INT) INTO rows_deleted;

    -- Если записей нет, уведомляем и выходим
    IF rows_deleted = 0 THEN
        RAISE NOTICE 'Записи с % = "%" не найдены.', field_name, search_value;
        PERFORM dblink_disconnect('myconn');
        RETURN 0;
    END IF;

    -- Удаляем записи
    PERFORM dblink_exec('myconn', 
        format('DELETE FROM trainings WHERE %I ILIKE %L', field_name, '%' || search_value || '%'));
	
    PERFORM dblink_disconnect('myconn');

    -- Подтверждаем удаление
    RAISE NOTICE 'Удалено % записей.', rows_deleted;
    RETURN rows_deleted;
END;
$$ LANGUAGE plpgsql;


--7. Обновление записи по ID и значению field в базе данных 
CREATE OR REPLACE FUNCTION update_training_field(
    db_name TEXT,
    p_id INT,
    p_field_name TEXT,
    p_new_value TEXT
)
RETURNS INT
AS $$ 
DECLARE
    db_exists BOOLEAN;
    table_exists BOOLEAN;
    field_exists BOOLEAN;
    connection_string TEXT;
    sql_query TEXT;
    updated_id INT;
BEGIN
    -- Проверяем существование базы данных через dblink
    SELECT EXISTS (
        SELECT 1 FROM dblink('dbname=trainings user=postgres password=CHocolate75% host=localhost',
                             'SELECT 1 FROM pg_database WHERE datname = ''' || db_name || '''')
        AS t(exists BOOLEAN)
    ) INTO db_exists;

    IF NOT db_exists THEN
        RAISE NOTICE 'Ошибка: база данных "%" не существует.', db_name;
        RETURN -2; -- Код ошибки для отсутствующей базы данных
    END IF;

    connection_string := 'dbname=trainings user=postgres password=CHocolate75% host=localhost';
    PERFORM dblink_connect('myconn', connection_string);

    -- Проверяем существование таблицы trainings
    SELECT EXISTS (
        SELECT 1 FROM dblink('myconn', 
                             'SELECT 1 FROM information_schema.tables WHERE table_schema = ''public'' AND table_name = ''trainings''')
        AS t(exists BOOLEAN)
    ) INTO table_exists;

    IF NOT table_exists THEN
        RAISE NOTICE 'Ошибка: таблица "trainings" не существует в базе "%".', db_name;
        PERFORM dblink_disconnect('myconn');
        RETURN -1; -- Код ошибки для отсутствующей таблицы
    END IF;

    -- Проверяем существование указанного столбца
    SELECT EXISTS (
        SELECT 1 FROM dblink('myconn',
            'SELECT 1 FROM information_schema.columns WHERE table_name = ''trainings'' 
             AND column_name = ''' || p_field_name || '''')
        AS t(exists BOOLEAN)
    ) INTO field_exists;

    IF NOT field_exists THEN
        RAISE NOTICE 'Ошибка: Поле "%" не существует.', p_field_name;
        PERFORM dblink_disconnect('myconn');
        RETURN -7; -- Код ошибки для некорректного поля
    END IF;

    -- Проверяем, что передан корректный ID
    IF p_id IS NULL OR p_id <= 0 THEN
        RAISE NOTICE 'Ошибка: Некорректный ID записи.';
        PERFORM dblink_disconnect('myconn');
        RETURN -9; -- Код ошибки некорректного ID
    END IF;

    -- Проверяем, что передано значение для обновления
    IF p_new_value IS NULL OR TRIM(p_new_value) = '' THEN
        RAISE NOTICE 'Ошибка: Новое значение не может быть пустым.';
        PERFORM dblink_disconnect('myconn');
        RETURN -10; -- Код ошибки для пустого значения
    END IF;

    -- Формируем SQL-запрос для обновления
    sql_query := format(
        'UPDATE trainings SET %I = %L WHERE class_id = %s RETURNING class_id', 
        p_field_name, p_new_value, p_id
    );
	
    SELECT class_id FROM dblink('myconn', sql_query) AS t(class_id INT) INTO updated_id;

    PERFORM dblink_disconnect('myconn');

    -- Проверяем, обновилась ли запись
    IF updated_id IS NULL THEN
        RAISE NOTICE 'Ошибка: Запись с ID % не найдена или поле не изменилось.', p_id;
        RETURN 0;
    END IF;

    RAISE NOTICE 'Запись с ID % обновлена.', updated_id;
    RETURN updated_id;
END;
$$ LANGUAGE plpgsql;

--8. Обновление записи по ID целиком
CREATE OR REPLACE FUNCTION update_training_record(
	db_name TEXT,
    p_id INT,
    p_title VARCHAR(255) DEFAULT NULL,
    p_date DATE DEFAULT NULL,
    p_start_time TIME DEFAULT NULL,
    p_duration INTERVAL DEFAULT NULL,
    p_max_participants INT DEFAULT NULL,
    p_current_participants INT DEFAULT NULL,
    p_difficulty_level VARCHAR(20) DEFAULT NULL,
    p_trainer_name VARCHAR(255) DEFAULT NULL,
    p_price DECIMAL(10,2) DEFAULT NULL
) 
RETURNS INT 
AS $$
DECLARE
	db_exists BOOLEAN;
    table_exists BOOLEAN;
    connection_string TEXT;
    update_query TEXT := 'UPDATE trainings SET ';
    updates TEXT := '';
    is_first BOOLEAN := TRUE;
    rows_updated INT := 0;
BEGIN
	-- Проверяем существование базы данных через dblink
    SELECT EXISTS (
        SELECT 1 FROM dblink('dbname=trainings user=postgres password=CHocolate75% host=localhost',
                             'SELECT 1 FROM pg_database WHERE datname = ''' || db_name || '''')
        AS t(exists BOOLEAN)
    ) INTO db_exists;

    IF NOT db_exists THEN
        RAISE NOTICE 'Ошибка: база данных "%" не существует.', db_name;
        RETURN -2; -- Код ошибки для отсутствующей базы данных
    END IF;

    connection_string := 'dbname=trainings user=postgres password=CHocolate75% host=localhost';
    PERFORM dblink_connect('myconn', connection_string);

    -- Проверяем существование таблицы trainings
    SELECT EXISTS (
        SELECT 1 FROM dblink('myconn', 
                             'SELECT 1 FROM information_schema.tables WHERE table_schema = ''public'' AND table_name = ''trainings''')
        AS t(exists BOOLEAN)
    ) INTO table_exists;

    IF NOT table_exists THEN
        RAISE NOTICE 'Ошибка: таблица "trainings" не существует в базе "%".', db_name;
        PERFORM dblink_disconnect('myconn');
        RETURN -1; -- Код ошибки для отсутствующей таблицы
    END IF;

	 -- Проверяем, что передан корректный ID
    IF p_id IS NULL OR p_id <= 0 THEN
        RAISE NOTICE 'Ошибка: Некорректный ID записи.';
        PERFORM dblink_disconnect('myconn');
        RETURN -9; -- Код ошибки некорректного ID
    END IF;
	
	-- Динамически формируем список полей для обновления
    IF p_title IS NOT NULL THEN
        updates := updates || format('%s title = %L', CASE WHEN is_first THEN '' ELSE ', ' END, p_title);
        is_first := FALSE;
    END IF;
    IF p_date IS NOT NULL THEN
        updates := updates || format('%s date = %L', CASE WHEN is_first THEN '' ELSE ', ' END, p_date);
        is_first := FALSE;
    END IF;
    IF p_start_time IS NOT NULL THEN
        updates := updates || format('%s start_time = %L', CASE WHEN is_first THEN '' ELSE ', ' END, p_start_time);
        is_first := FALSE;
    END IF;
    IF p_duration IS NOT NULL THEN
        updates := updates || format('%s duration = %L', CASE WHEN is_first THEN '' ELSE ', ' END, p_duration);
        is_first := FALSE;
    END IF;
    IF p_max_participants IS NOT NULL THEN
        updates := updates || format('%s max_participants = %L', CASE WHEN is_first THEN '' ELSE ', ' END, p_max_participants);
        is_first := FALSE;
    END IF;
    IF p_current_participants IS NOT NULL THEN
        updates := updates || format('%s current_participants = %L', CASE WHEN is_first THEN '' ELSE ', ' END, p_current_participants);
        is_first := FALSE;
    END IF;
    IF p_difficulty_level IS NOT NULL THEN
        updates := updates || format('%s difficulty_level = %L', CASE WHEN is_first THEN '' ELSE ', ' END, p_difficulty_level);
        is_first := FALSE;
    END IF;
    IF p_trainer_name IS NOT NULL THEN
        updates := updates || format('%s trainer_name = %L', CASE WHEN is_first THEN '' ELSE ', ' END, p_trainer_name);
        is_first := FALSE;
    END IF;
    IF p_price IS NOT NULL THEN
        updates := updates || format('%s price = %L', CASE WHEN is_first THEN '' ELSE ', ' END, p_price);
        is_first := FALSE;
    END IF;

    -- Если обновлять нечего, выходим
    IF updates = '' THEN
        RAISE NOTICE 'Ошибка: Нет данных для обновления.';
        PERFORM dblink_disconnect('myconn');
        RETURN -10; -- Код ошибки для пустого обновления
    END IF;

    -- Формируем финальный SQL-запрос
    update_query := update_query || updates || format(' WHERE class_id = %s RETURNING class_id', p_id);

    SELECT updated_id FROM dblink('myconn', update_query) AS t(updated_id INT) INTO rows_updated;

    PERFORM dblink_disconnect('myconn');

    -- Проверяем, была ли запись обновлена
    IF rows_updated IS NULL OR rows_updated = 0 THEN
        RAISE NOTICE 'Ошибка: Запись с ID % не найдена.', p_id;
        RETURN 0;
    END IF;
	
    RAISE NOTICE 'Запись с ID % обновлена.', rows_updated;
    RETURN rows_updated;
END;
$$ LANGUAGE plpgsql;

--9. Просмотр базы данных
CREATE OR REPLACE FUNCTION get_all_trainings(db_name TEXT)
RETURNS TABLE(
    id INT,
    title VARCHAR(255),
    date DATE,
    start_time TIME,
    duration INTERVAL,
    max_participants INT,
    current_participants INT,
    difficulty_level VARCHAR(20),
    trainer_name VARCHAR(255),
    price DECIMAL(10,2)
) AS $$
DECLARE
	db_exists BOOLEAN;
    connection_string TEXT;
    sql_query TEXT;
    query_result RECORD;
BEGIN
	-- Проверяем, существует ли база данных через dblink
    SELECT EXISTS (
        SELECT 1 FROM dblink('dbname=trainings user=postgres password=CHocolate75%',
                             'SELECT 1 FROM pg_database WHERE datname = ''' || db_name || '''')
        as t(exists boolean)
    ) into db_exists;

	IF NOT db_exists THEN
	    RAISE NOTICE 'Ошибка: база данных % не существует.', db_name;
		RETURN;
	END IF;
	
   connection_string := 'dbname=trainings user=postgres password=CHocolate75% host=localhost';
   perform dblink_connect('myconn', connection_string);

    -- Проверяем, существует ли таблица trainings
    SELECT EXISTS (
        SELECT 1 FROM dblink('myconn', 
                             'SELECT 1 FROM information_schema.tables WHERE table_name = ''trainings''')
        AS t(exists BOOLEAN)
    ) INTO query_result;

    IF NOT query_result.exists THEN
        RAISE NOTICE 'Ошибка: таблица trainings не существует в базе %.', db_name;
        PERFORM dblink_disconnect('myconn');
        RETURN;
    END IF;

    sql_query := 'SELECT class_id, title, date, start_time, duration, max_participants, 
                         current_participants, difficulty_level, trainer_name, price 
                  FROM trainings';
    RETURN QUERY 
    SELECT * FROM dblink('myconn', sql_query) 
    AS t(class_id INT, title VARCHAR(255), date DATE, start_time TIME, duration INTERVAL, 
         max_participants INT, current_participants INT, difficulty_level VARCHAR(20), 
         trainer_name VARCHAR(255), price DECIMAL(10,2));

    PERFORM dblink_disconnect('myconn');
END;
$$ LANGUAGE plpgsql;

-- Функция создания пользователя с заданным режимом доступа
CREATE OR REPLACE FUNCTION create_user(
    p_username TEXT,   
    p_password TEXT,   
    p_role TEXT       
) RETURNS INT AS $$
DECLARE
    connections TEXT[];
	connection_string text;
    inserted_id INT; -- Идентификатор нового пользователя
BEGIN	
	select dblink_get_connections() into connections;
    if 'new_database' = any(connections) then
        PERFORM dblink_disconnect('new_database');
    end if;
	
    -- Проверяем корректность роли (admin / guest)
    IF p_role NOT IN ('admin', 'guest') THEN
        RAISE NOTICE 'Ошибка: Некорректная роль "%". Разрешены только "admin" или "guest".', p_role;
        RETURN -11;  -- Ошибка: некорректная роль
    END IF;

    -- Вставка нового пользователя в таблицу users
    BEGIN
        INSERT INTO users(username, password, role) 
        VALUES (p_username, p_password, p_role)
        RETURNING id INTO inserted_id; -- Получаем ID нового пользователя
    EXCEPTION
        WHEN unique_violation THEN
            RAISE NOTICE 'Ошибка: пользователь "%" уже существует.', p_username;
            RETURN -13;  -- Ошибка: пользователь уже существует
        WHEN others THEN
            RAISE NOTICE 'Ошибка при добавлении пользователя в таблицу users: %', SQLERRM;
            RETURN -14;  -- Ошибка: пользователь не добавлен в таблицу
    END;

    connection_string := 'dbname=training_schedule user=postgres password=CHocolate75% host=localhost';
    PERFORM dblink_connect('train_conn', connection_string);

    -- Создаем пользователя в PostgreSQL
    BEGIN
        PERFORM dblink_exec('train_conn', format('CREATE USER %I WITH PASSWORD %L', p_username, p_password));
    EXCEPTION
        WHEN others THEN
            RAISE NOTICE 'Ошибка при создании пользователя: %', SQLERRM;
            PERFORM dblink_disconnect('train_conn');
            RETURN -12;  -- Ошибка: пользователь не создан
    END;

    -- Назначаем права в зависимости от роли
    IF p_role = 'admin' THEN
        -- Права на базу данных
        PERFORM dblink_exec('train_conn', format('GRANT ALL PRIVILEGES ON DATABASE training_schedule TO %I', p_username));

        -- Права на схему public
        PERFORM dblink_exec('train_conn', format('GRANT ALL PRIVILEGES ON SCHEMA public TO %I', p_username));

        -- Права на будущие таблицы
        PERFORM dblink_exec('train_conn', format('ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO %I', p_username));
    ELSIF p_role = 'guest' THEN
        -- Права на базу данных
        PERFORM dblink_exec('train_conn', format('GRANT CONNECT ON DATABASE training_schedule TO %I', p_username));

        -- Права на схему public
        PERFORM dblink_exec('train_conn', format('GRANT USAGE ON SCHEMA public TO %I', p_username));

        -- Права на будущие таблицы
        PERFORM dblink_exec('train_conn', format('ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO %I', p_username));
    END IF;

    -- Закрываем соединение с training_schedule
    PERFORM dblink_disconnect('train_conn');

    -- Успешное создание
    RAISE NOTICE 'Пользователь "%" успешно создан с ролью "%".', p_username, p_role;
    RETURN inserted_id;  -- Возвращаем ID нового пользователя
END;
$$ LANGUAGE plpgsql;

select * from users;
-- select create_user('guest5', 'guest5', 'guest');



