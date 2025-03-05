package db;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DBManager {
    private static final String BASE_URL = "jdbc:postgresql://localhost:5432/";
    private static final String DATABASE_NAME  = "trainings";

    // Создание базы данных
    public static String createDatabase(String dbName, String username, String password) {
        try (Connection conn = DriverManager.getConnection(BASE_URL + DATABASE_NAME, username, password);
             CallableStatement stmt = conn.prepareCall("{ call create_database(?) }")) {
            stmt.setString(1, dbName); // Передаем имя БД
            stmt.execute();
            return "База данных " + dbName + " создана (или уже существовала).";
        } catch (SQLException e) {
            return "Ошибка при создании базы данных: " + e.getMessage();
        }
    }

    // Вызов хранимой процедуры для создания таблицы в training_schedule
    public static String createTable(String dbName, String username, String password) {
        try (Connection conn = DriverManager.getConnection(BASE_URL + DATABASE_NAME, username, password);
             CallableStatement stmt = conn.prepareCall("{ CALL create_table(?, ?) }")) {
            stmt.setString(1, dbName);
            stmt.registerOutParameter(2, java.sql.Types.INTEGER);
            stmt.execute();

            int tableCreatedCode = stmt.getInt(2);
            if (tableCreatedCode == -2) {
                return "База данных training_schedule не была создана.";
            } else if (tableCreatedCode == -1) {
                return "Таблица trainings уже существует.";
            } else {
                return "Таблица trainings успешно создана!";
            }
        } catch (SQLException e) {
            return "Ошибка при создании таблицы: " + e.getMessage();
        }
    }

    // Удаление базы данных
    public static String dropDatabase(String dbName, String username, String password) {
        try (Connection conn = DriverManager.getConnection(BASE_URL + DATABASE_NAME, username, password);
             CallableStatement stmt = conn.prepareCall("{ call drop_database(?, ?) }")) {
            stmt.setString(1, dbName);
            stmt.registerOutParameter(2, java.sql.Types.INTEGER);
            stmt.execute();

            int result = stmt.getInt(2);
            if (result == 1) {
                return "База данных " + dbName + " успешно удалена.";
            } else if (result == -2) {
                return "Ошибка: база данных " + dbName + " не существует.";
            } else {
                return "Неизвестный результат удаления базы данных: " + result;
            }
        } catch (SQLException e) {
            return "Ошибка при удалении базы данных: " + e.getMessage();
        }
    }

    // Метод для создания пользователя
    public static void createUser(String username, String password, String role) {
        try (Connection conn = DriverManager.getConnection(BASE_URL + DATABASE_NAME, "postgres", "CHocolate75%"); // Используем суперпользователя
             CallableStatement stmt = conn.prepareCall("{ ? = call create_user(?, ?, ?) }")) {
            stmt.registerOutParameter(1, java.sql.Types.INTEGER);
            stmt.setString(2, username);
            stmt.setString(3, password);
            stmt.setString(4, role);

            stmt.execute();
            int result = stmt.getInt(1);
            switch (result) {
                case -2:
                    System.out.println("Ошибка: база данных не существует.");
                    break;
                case -13:
                    System.out.println("Ошибка: пользователь уже существует.");
                    break;
                case -11:
                    System.out.println("Ошибка: Некорректная роль. Разрешены только \"admin\" или \"guest\".");
                    break;
                case -12:
                    System.out.println("Ошибка: пользователь не создан.");
                    break;
                default:
                    System.out.println("Пользователь " + username + " создан с ролью " + role);
            }
        } catch (SQLException e) {
            System.out.println("Ошибка при создании пользователя: " + e.getMessage());
        }
    }

    // Очистка базы данных
    public static String clearDatabase(String dbName, String username, String password) {
        try (Connection conn = DriverManager.getConnection(BASE_URL + DATABASE_NAME, username, password);
             CallableStatement stmt = conn.prepareCall("{ ? = CALL clear_database(?) }")) {
            stmt.registerOutParameter(1, java.sql.Types.INTEGER);
            stmt.setString(2, dbName);
            stmt.execute();

            int result = stmt.getInt(1);

            if (result == -2) {
                return "Ошибка: база данных " + dbName + " не существует.";
            } else if (result == -1) {
                return "Ошибка: таблица trainings не существует.";
            } else {
                return "База данных " + dbName + " успешно очищена! Удалено " + result + " записей.";
            }
        } catch (SQLException e) {
            return "Ошибка при очистке базы данных: " + e.getMessage();
        }
    }

    // Добавление новых данных
    public static String addTraining(String dbName, String title, Date date, Time startTime, String duration,
                                     int maxParticipants, int currentParticipants, String difficultyLevel,
                                     String trainerName, double price, String username, String password) {
        try (Connection conn = DriverManager.getConnection(BASE_URL + DATABASE_NAME, username, password);
             CallableStatement stmt = conn.prepareCall("{ ? = call add_new_training(?, ?, ?, ?, ?, ?, ?, ?, ?, ?) }")) {
            stmt.registerOutParameter(1, java.sql.Types.INTEGER);
            stmt.setString(2, dbName);
            stmt.setString(3, title);
            stmt.setDate(4, date);
            stmt.setTime(5, startTime);
            stmt.setObject(6, duration, Types.OTHER);
            stmt.setInt(7, maxParticipants);
            stmt.setInt(8, currentParticipants);
            stmt.setString(9, difficultyLevel);
            stmt.setString(10, trainerName);
            stmt.setBigDecimal(11, BigDecimal.valueOf(price));

            stmt.execute();
            int newTrainingId = stmt.getInt(1);

            // Обработка возможных кодов ошибок
            if (newTrainingId == -2) {
                return "Ошибка: база данных " + dbName + " не существует.";
            } else if (newTrainingId == -1) {
                return "Ошибка: таблица trainings не существует.";
            } else if (newTrainingId == -3) {
                return "Ошибка: максимальное количество участников должно быть положительным.";
            } else if (newTrainingId == -4) {
                return "Ошибка: текущее количество участников должно быть неотрицательным.";
            } else if (newTrainingId == -5) {
                return "Ошибка: некорректный уровень сложности. Допустимы только: \"смешанный\", \"начальный\" и \"продвинутый\"";
            } else if (newTrainingId == -6) {
                return "Ошибка: время проведения занятия у тренера " + trainerName + " пересекается с его другим занятием.";
            } else if (newTrainingId > 0) {
                return "Тренировка успешно добавлена с ID " + newTrainingId;
            } else {
                return "Неизвестная ошибка при добавлении тренировки.";
            }
        } catch (SQLException e) {
            return "Ошибка при добавлении тренировки: " + e.getMessage();
        }
    }

    // Поиск тренировок по текстовому полю
    public static List<String[]> searchTrainingByField(String dbName, String fieldName, String searchValue, String username, String password) {
        List<String[]> results = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(BASE_URL + DATABASE_NAME, username, password);
             CallableStatement stmt = conn.prepareCall("{ call search_training_by_field(?, ?, ?) }")) {
            stmt.setString(1, dbName);
            stmt.setString(2, fieldName);
            stmt.setString(3, searchValue);

            ResultSet rs = stmt.executeQuery();
            boolean hasResults = false; // Флаг для проверки наличия данных

            while (rs.next()) {
                hasResults = true;
                String[] row = new String[10]; // Количество столбцов в таблице
                row[0] = String.valueOf(rs.getInt("id")); // ID
                row[1] = rs.getString("title"); // Название
                row[2] = rs.getDate("date").toString(); // Дата
                row[3] = rs.getTime("start_time").toString(); // Время
                row[4] = rs.getString("duration"); // Длительность
                row[5] = rs.wasNull() ? "N/A" : String.valueOf(rs.getInt("max_participants")); // Макс. участников
                row[6] = rs.wasNull() ? "N/A" : String.valueOf(rs.getInt("current_participants")); // Текущие участники
                row[7] = rs.getString("difficulty_level") != null ? rs.getString("difficulty_level") : "N/A"; // Уровень сложности
                row[8] = rs.getString("trainer_name"); // Тренер
                row[9] = rs.wasNull() ? "0.0" : String.format("%.2f", rs.getDouble("price")); // Цена
                results.add(row);
            }

            // Если записей не нашлось, добавляем сообщение
            if (!hasResults) {
                results.add(new String[]{"Записи не найдены по полю '" + fieldName + "' со значением '" + searchValue + "'"});
            }
        } catch (SQLException e) {
            System.out.println("Ошибка при поиске тренировок: " + e.getMessage());
            results.add(new String[]{"Ошибка при поиске тренировок: " + e.getMessage()});
        }
        return results;
    }

    // Удаление тренировок по полю
    public static int deleteTrainingByField(String dbName, String fieldName, String searchValue, String username, String password) {
        int rowsDeleted;
        try (Connection conn = DriverManager.getConnection(BASE_URL + DATABASE_NAME, username, password);             CallableStatement stmt = conn.prepareCall("{ ? = call delete_trainings_by_field(?, ?, ?) }")) {
            stmt.registerOutParameter(1, Types.INTEGER);
            stmt.setString(2, dbName);
            stmt.setString(3, fieldName);
            stmt.setString(4, searchValue);

            stmt.execute();
            rowsDeleted = stmt.getInt(1); // Получаем результат удаления

            // Обрабатываем возможные коды возврата
            if (rowsDeleted == -2) {
                System.out.println("Ошибка: база данных " + dbName + " не существует.");
            } else if (rowsDeleted == -1) {
                System.out.println("Ошибка: таблица trainings не существует.");
            } else if (rowsDeleted == -7) {
                System.out.println("Ошибка: Поле " + fieldName + " не существует или не является текстовым.");
            } else if (rowsDeleted == -8) {
                System.out.println("Ошибка: Значение для удаления не может быть пустым.");
            } else if (rowsDeleted == 0) {
                System.out.println("Записи не найдены.");
            } else {
                System.out.println("Удалено " + rowsDeleted + " записей.");
            }
        }
        catch (SQLException e) {
            System.out.println(e.getMessage());
            return -555; // Код ошибки при исключении
        }
        return rowsDeleted;
    }

    // Обновление определенного поля тренировки по ID
    public static int updateTrainingByField(String dbName, int id, String fieldName, String newValue, String username, String password) {
        int result;
        try (Connection conn = DriverManager.getConnection(BASE_URL + DATABASE_NAME, username, password);             CallableStatement stmt = conn.prepareCall("{ ? = call update_training_field(?, ?, ?, ?) }")) {
            stmt.registerOutParameter(1, Types.INTEGER);
            stmt.setString(2, dbName);
            stmt.setInt(3, id);
            stmt.setString(4, fieldName);
            stmt.setString(5, newValue);

            stmt.execute();
            result = stmt.getInt(1);
            // Обрабатываем возможные коды возврата
            if (result == -2) {
                System.out.println("Ошибка: база данных " + dbName + " не существует.");
            } else if (result == -1) {
                System.out.println("Ошибка: таблица trainings не существует.");
            } else if (result == -7) {
                System.out.println("Ошибка: Поле " + fieldName + " не существует или не является текстовым.");
            }
            else if (result == -9) {
                System.out.println("Ошибка: Некорректный ID");
            } else if (result == -10) {
                System.out.println("Ошибка: Значение для обновления не может быть пустым.");
            } else if (result == 0) {
                System.out.println("Запись с ID " + id + " не найдена.");
            } else {
                System.out.println("Запись успешно обновлена!");
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return -555; // Код ошибки при исключении
        }
        return result;
    }

    // Полное обновление тренировки по ID
    public static int updateTrainingRecord(String dbName, int id, String title, Date date, Time startTime, String duration,
                                           Integer maxParticipants, Integer currentParticipants, String difficultyLevel,
                                           String trainerName, Double price, String username, String password) {
        int result;
        try (Connection conn = DriverManager.getConnection(BASE_URL + DATABASE_NAME, username, password);             CallableStatement stmt = conn.prepareCall("{ ? = call update_training_record(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}")) {
            stmt.registerOutParameter(1, Types.INTEGER);
            stmt.setString(2, dbName);
            stmt.setInt(3, id);
            stmt.setString(4, title);
            stmt.setDate(5, date);
            stmt.setTime(6, startTime);
            stmt.setObject(7, duration, Types.OTHER);
            stmt.setInt(8, maxParticipants);
            stmt.setInt(9, currentParticipants);
            stmt.setString(10, difficultyLevel);
            stmt.setString(11, trainerName);
            stmt.setBigDecimal(12, BigDecimal.valueOf(price));

            stmt.execute();
            result = stmt.getInt(1);
            // Обрабатываем возможные коды возврата
            if (result == -2) {
                System.out.println("Ошибка: база данных " + dbName + " не существует.");
            } else if (result == -1) {
                System.out.println("Ошибка: таблица trainings не существует.");
            } else if (result == -9) {
                System.out.println("Ошибка: Некорректный ID");
            } else if (result == -10) {
                System.out.println("Ошибка: Нет данных для обновления.");
            } else if (result == 0) {
                System.out.println("Запись с ID " + id + " не найдена.");
            } else {
                System.out.println("Запись с ID " + id + " успешно обновлена.");
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return -555; // Код ошибки при исключении
        }
        return result;
    }

    // Просмотр тренировок
    public static List<String[]> getAllTrainings(String dbName, String username, String password) {
        List<String[]> results = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(BASE_URL + DATABASE_NAME, username, password);
             CallableStatement stmt = conn.prepareCall("{ call get_all_trainings(?) }")) {
            stmt.setString(1, dbName);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String[] row = new String[10]; // Количество столбцов в таблице
                row[0] = String.valueOf(rs.getInt("id")); // ID
                row[1] = rs.getString("title"); // Название
                row[2] = rs.getDate("date").toString(); // Дата
                row[3] = rs.getTime("start_time").toString(); // Время
                row[4] = rs.getString("duration"); // Длительность
                row[5] = rs.wasNull() ? "N/A" : String.valueOf(rs.getInt("max_participants")); // Макс. участников
                row[6] = rs.wasNull() ? "N/A" : String.valueOf(rs.getInt("current_participants")); // Текущие участники
                row[7] = rs.getString("difficulty_level") != null ? rs.getString("difficulty_level") : "N/A"; // Уровень сложности
                row[8] = rs.getString("trainer_name"); // Тренер
                row[9] = rs.wasNull() ? "0.0" : String.format("%.2f", rs.getDouble("price")); // Цена
                results.add(row);
            }
        } catch (SQLException e) {
            System.out.println("Ошибка при загрузке тренировок: " + e.getMessage());
        }
        return results;
    }

}
