package gui;

import db.DBManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.*;
import java.util.List;

import static db.DBManager.addTraining;

public class TrainingsManager extends JFrame {
    private static final String BASE_URL = "jdbc:postgresql://localhost:5432/";
    private static final String DATABASE_NAME  = "trainings";
    private JTable trainingsTable;
    private DefaultTableModel tableModel;

    private JTextField usernameField;
    private JPasswordField passwordField;
    private JTextArea outputArea;

    private JButton createDBButton;
    private JButton createTableButton;
    private JButton viewTrainingsButton;
    private JButton addTrainingButton;
    private JButton searchTrainingButton;
    private JButton deleteTrainingButton;
    private JButton updateTrainingButton;
    private JButton clearDBButton;
    private JButton dropDBButton;
    private JScrollPane tableScrollPane;

    public TrainingsManager() {
        setTitle("Role-Based Access Application");
        setSize(800, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setLocationRelativeTo(null);

        createDBButton = new JButton("Создать базу данных");
        createTableButton = new JButton("Создать таблицу");
        viewTrainingsButton = new JButton("Просмотр тренировок");
        addTrainingButton = new JButton("Добавить тренировку");
        searchTrainingButton = new JButton("Найти тренировку");
        deleteTrainingButton = new JButton("Удалить тренировку");
        updateTrainingButton = new JButton("Обновить тренировку");
        clearDBButton = new JButton("Очистить базу данных");
        dropDBButton = new JButton("Удалить базу данных");

        createDBButton.addActionListener(e -> performActionWithRole("createDatabase"));
        createTableButton.addActionListener(e -> performActionWithRole("createTable"));
        viewTrainingsButton.addActionListener(e -> performActionWithRole("getAllTrainings"));
        addTrainingButton.addActionListener(e -> performActionWithRole("addTraining"));
        searchTrainingButton.addActionListener(e -> performActionWithRole("searchTraining"));
        deleteTrainingButton.addActionListener(e -> performActionWithRole("deleteTraining"));
        updateTrainingButton.addActionListener(e -> performActionWithRole("updateTraining"));
        clearDBButton.addActionListener(e -> performActionWithRole("clearDatabase"));
        dropDBButton.addActionListener(e -> performActionWithRole("dropDatabase"));


        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(3, 3)); // 4 строки, 2 столбца

        buttonPanel.add(createDBButton);
        buttonPanel.add(createTableButton);
        buttonPanel.add(viewTrainingsButton);
        buttonPanel.add(addTrainingButton);
        buttonPanel.add(searchTrainingButton);
        buttonPanel.add(deleteTrainingButton);
        buttonPanel.add(updateTrainingButton);
        buttonPanel.add(clearDBButton);
        buttonPanel.add(dropDBButton);

        add(buttonPanel, BorderLayout.SOUTH);

        tableModel = new DefaultTableModel();
        tableModel.addColumn("ID");
        tableModel.addColumn("Название");
        tableModel.addColumn("Дата");
        tableModel.addColumn("Время");
        tableModel.addColumn("Длительность");
        tableModel.addColumn("Макс. участников");
        tableModel.addColumn("Текущие участники");
        tableModel.addColumn("Уровень сложности");
        tableModel.addColumn("Тренер");
        tableModel.addColumn("Цена");

        trainingsTable = new JTable(tableModel);
        tableScrollPane = new JScrollPane(trainingsTable);

        add(tableScrollPane, BorderLayout.CENTER);

        JPanel loginPanel = new JPanel();
        loginPanel.setLayout(new GridLayout(3, 2));

        loginPanel.add(new JLabel("Username:"));
        usernameField = new JTextField();
        loginPanel.add(usernameField);

        loginPanel.add(new JLabel("Password:"));
        passwordField = new JPasswordField();
        loginPanel.add(passwordField);

        JButton loginButton = new JButton("Login");
        loginButton.addActionListener(new LoginActionListener());
        loginPanel.add(loginButton);

        add(loginPanel, BorderLayout.NORTH);

        outputArea = new JTextArea();
        outputArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(outputArea);
        add(scrollPane, BorderLayout.SOUTH);
        add(buttonPanel, BorderLayout.SOUTH);

        setButtonsVisibility(false);
        setVisible(true);
    }

    private void setButtonsVisibility(boolean isVisible) {
        createDBButton.setVisible(isVisible);
        createTableButton.setVisible(isVisible);
        viewTrainingsButton.setVisible(isVisible); // Видна для всех
        addTrainingButton.setVisible(isVisible);
        searchTrainingButton.setVisible(isVisible);
        deleteTrainingButton.setVisible(isVisible);
        updateTrainingButton.setVisible(isVisible);
        clearDBButton.setVisible(isVisible);
        dropDBButton.setVisible(isVisible);
        tableScrollPane.setVisible(isVisible);
    }

    private class LoginActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());

            // Authenticate user and get role
            String role = authenticateUser(username, password);
            if (role != null) {
                outputArea.setText("Login successful! Role: " + role + "\n");
                // Display appropriate interface based on role
                if (role.equals("admin")) {
                    provideAdminAccess(username, password);
                } else {
                    provideGuestAccess(username, password);
                }
            } else {
                outputArea.setText("Login failed! Invalid username or password.\n");
            }
        }
    }

    private String authenticateUser(String username, String password) {
        String role = null;
        try (Connection conn = DriverManager.getConnection(BASE_URL + DATABASE_NAME, "postgres", "CHocolate75%");
             Statement stmt = conn.createStatement()) {
            String query = "SELECT role FROM users WHERE username = '" + username + "' AND password = '" + password + "'";
            ResultSet rs = stmt.executeQuery(query);
            if (rs.next()) {
                role = rs.getString("role");
            }
        } catch (SQLException ex) {
            outputArea.append("Error during authentication: " + ex.getMessage() + "\n");
        }
        return role;
    }

    // Метод для предоставления доступа гостя
    public Connection provideGuestAccess(String username, String password) {
        Connection connection = null;
        try {
            // Установка соединения с базой данных
            String url = "jdbc:postgresql://localhost:5432/trainings";
            connection = DriverManager.getConnection(url, username, password);
            JOptionPane.showMessageDialog(null, "Успешное подключение к базе данных как гость.");
            setButtonsVisibility(true);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Ошибка подключения к базе данных: " + e.getMessage());
        }
        return connection;
    }

    // Метод для предоставления доступа администратору
    public Connection provideAdminAccess(String username, String password) {
        Connection connection = null;

        // Проверка логина и пароля администратора
        try {
            // Установка соединения с базой данных с правами администратора
            String url = "jdbc:postgresql://localhost:5432/trainings";
            connection = DriverManager.getConnection(url, username, password);
            JOptionPane.showMessageDialog(null, "Успешное подключение к базе данных как администратор.");
            setButtonsVisibility(true);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Ошибка подключения к базе данных: " + e.getMessage());
        }
        return connection;
    }

    private void performActionWithRole(String action) {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        String role = authenticateUser(username, password);
        if (role == null) {
            JOptionPane.showMessageDialog(null, "Пользователь не аутентифицирован.");
            return;
        }

        if (role.equals("guest") && (action.equals("createDatabase") || action.equals("clearDatabase") || action.equals("dropDatabase") ||
                action.equals("addTraining") || action.equals("deleteTraining") || action.equals("updateTraining") || action.equals("createTable"))) {
            JOptionPane.showMessageDialog(null, "Ошибка: у вас нет прав для выполнения этого действия.");
            return;
        }

        try (Connection conn = DriverManager.getConnection(BASE_URL + DATABASE_NAME, username, password)) {
            String resultMessage;
            switch (action) {
                case "createDatabase":
                    resultMessage = DBManager.createDatabase("training_schedule", username, password);
                    JOptionPane.showMessageDialog(null, resultMessage);
                    break;
                case "createTable":
                    resultMessage = DBManager.createTable("training_schedule", username, password);
                    JOptionPane.showMessageDialog(null, resultMessage);
                    break;
                case "getAllTrainings":
                    List<String[]> trainings = DBManager.getAllTrainings("training_schedule", username, password);
                    updateTrainingsTable(trainings); // Обновляем таблицу данными
                    break;
                case "addTraining":
                    openAddTrainingDialog(username, password);
                    refreshTrainingsTable();
                    break;
                case "deleteTraining":
                    performDeleteTraining(username, password);
                    refreshTrainingsTable();
                    break;
                case "updateTraining":
                    openUpdateTrainingDialog(username, password);
                    refreshTrainingsTable();
                    break;
                case "searchTraining":
                    performSearch(username, password);
                    break;
                case "clearDatabase":
                    resultMessage = DBManager.clearDatabase("training_schedule", username, password);
                    JOptionPane.showMessageDialog(null, resultMessage);
                    refreshTrainingsTable();
                    break;
                case "dropDatabase":
                    resultMessage = DBManager.dropDatabase("training_schedule", username, password);
                    JOptionPane.showMessageDialog(null, resultMessage);
                    break;
                default:
                    JOptionPane.showMessageDialog(null, "Неизвестное действие.");
            }
        } catch (SQLException e) {
            if (e.getSQLState().equals("42501")) {
                JOptionPane.showMessageDialog(null, "Ошибка: у вас нет прав для выполнения этого действия.");
            } else {
                JOptionPane.showMessageDialog(null, "Ошибка при выполнении запроса: " + e.getMessage());
            }
        }
    }

    // Обновление таблицы после изменений
    private void updateTrainingsTable(List<String[]> trainings) {
        tableModel.setRowCount(0);

        for (String[] row : trainings) {
            tableModel.addRow(row);
        }

        trainingsTable.revalidate();
        trainingsTable.repaint();
    }

    // Обновление таблицы после изменений
    private void refreshTrainingsTable() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        List<String[]> trainings = DBManager.getAllTrainings("training_schedule", username, password);

        if (trainings != null) {
            updateTrainingsTable(trainings);
        } else {
            JOptionPane.showMessageDialog(null, "Не удалось загрузить данные о тренировках.");
        }
    }

    // Поиск
    private void performSearch(String username, String password) {
        // Запрашиваем у пользователя поле для поиска и значение
        String fieldName = JOptionPane.showInputDialog(null, "Введите поле для поиска (title / trainer_name / difficulty_level):");
        if (fieldName == null || fieldName.trim().isEmpty()) {
            JOptionPane.showMessageDialog(null, "Поле для поиска не может быть пустым.");
            return;
        }

        String searchValue = JOptionPane.showInputDialog(null, "Введите значение для поиска:");
        if (searchValue == null || searchValue.trim().isEmpty()) {
            JOptionPane.showMessageDialog(null, "Значение для поиска не может быть пустым.");
            return;
        }

        // Выполняем поиск
        List<String[]> searchResults = DBManager.searchTrainingByField("training_schedule", fieldName, searchValue, username, password);

        // Если результаты найдены, обновляем таблицу
        if (!searchResults.isEmpty() && !searchResults.get(0)[0].startsWith("Записи не найдены")) {
            updateTrainingsTable(searchResults); // Обновляем таблицу данными
        } else {
            // Если записи не найдены, показываем сообщение
            JOptionPane.showMessageDialog(null, searchResults.get(0)[0]);
        }
    }

    // Удаление
    private void performDeleteTraining(String username, String password) {
        // Запрашиваем у пользователя поле для удаления и значение
        String deleteFieldName = JOptionPane.showInputDialog(null, "Введите поле для удаления (например, title, trainer_name):");
        if (deleteFieldName == null || deleteFieldName.trim().isEmpty()) {
            JOptionPane.showMessageDialog(null, "Поле для удаления не может быть пустым.");
            return;
        }

        String deleteValue = JOptionPane.showInputDialog(null, "Введите значение для удаления:");
        if (deleteValue == null || deleteValue.trim().isEmpty()) {
            JOptionPane.showMessageDialog(null, "Значение для удаления не может быть пустым.");
            return;
        }

        // Выполняем удаление
        int rowsDeleted = DBManager.deleteTrainingByField("training_schedule", deleteFieldName, deleteValue, username, password);

        // Обрабатываем результат удаления
        if (rowsDeleted > 0) {
            JOptionPane.showMessageDialog(null, "Удалено " + rowsDeleted + " записей.");
            refreshTrainingsTable(); // Обновляем таблицу
        } else if (rowsDeleted == -2) {
            JOptionPane.showMessageDialog(null, "Ошибка: база данных training_schedule не существует.");
        } else if (rowsDeleted == -1) {
            JOptionPane.showMessageDialog(null, "Ошибка: таблица trainings не существует.");
        } else if (rowsDeleted == -7) {
            JOptionPane.showMessageDialog(null, "Ошибка: Поле " + deleteFieldName + " не существует или не является текстовым.");
        } else if (rowsDeleted == -8) {
            JOptionPane.showMessageDialog(null, "Ошибка: Значение для удаления не может быть пустым.");
        } else if (rowsDeleted == 0) {
            JOptionPane.showMessageDialog(null, "Записи не найдены.");
        } else {
            JOptionPane.showMessageDialog(null, "Ошибка при удалении записей.");
        }
    }

    // Обновление
    private void openUpdateTrainingDialog(String username, String password) {
        // Запрашиваем ID тренировки для обновления
        String idInput = JOptionPane.showInputDialog(null, "Введите ID тренировки для обновления:");
        if (idInput == null || idInput.trim().isEmpty()) {
            JOptionPane.showMessageDialog(null, "ID не может быть пустым.");
            return;
        }

        int id;
        try {
            id = Integer.parseInt(idInput);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(null, "Неверный формат ID.");
            return;
        }

        // Поля для ввода данных
        JTextField titleField = new JTextField();
        JTextField dateField = new JTextField();
        JTextField startTimeField = new JTextField();
        JTextField durationField = new JTextField();
        JTextField maxParticipantsField = new JTextField();
        JTextField currentParticipantsField = new JTextField();
        JTextField difficultyLevelField = new JTextField();
        JTextField trainerNameField = new JTextField();
        JTextField priceField = new JTextField();

        // Панель для размещения полей
        JPanel panel = new JPanel(new GridLayout(0, 2));
        panel.add(new JLabel("Название:"));
        panel.add(titleField);
        panel.add(new JLabel("Дата (гггг-мм-дд):"));
        panel.add(dateField);
        panel.add(new JLabel("Время начала (чч:мм:сс):"));
        panel.add(startTimeField);
        panel.add(new JLabel("Длительность:"));
        panel.add(durationField);
        panel.add(new JLabel("Макс. участников:"));
        panel.add(maxParticipantsField);
        panel.add(new JLabel("Текущие участники:"));
        panel.add(currentParticipantsField);
        panel.add(new JLabel("Уровень сложности:"));
        panel.add(difficultyLevelField);
        panel.add(new JLabel("Имя тренера:"));
        panel.add(trainerNameField);
        panel.add(new JLabel("Цена:"));
        panel.add(priceField);

        // Отображение диалогового окна
        int result = JOptionPane.showConfirmDialog(null, panel, "Обновить тренировку", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            try {
                // Получение данных из полей
                String title = titleField.getText();
                Date date = Date.valueOf(dateField.getText()); // Преобразуем строку в Date
                Time startTime = Time.valueOf(startTimeField.getText()); // Преобразуем строку в Time
                String duration = durationField.getText();
                int maxParticipants = Integer.parseInt(maxParticipantsField.getText());
                int currentParticipants = Integer.parseInt(currentParticipantsField.getText());
                String difficultyLevel = difficultyLevelField.getText();
                String trainerName = trainerNameField.getText();
                double price = Double.parseDouble(priceField.getText());

                int updateResult = DBManager.updateTrainingRecord("training_schedule", id, title, date, startTime, duration,
                        maxParticipants, currentParticipants, difficultyLevel, trainerName, price, username, password);

                if (updateResult > 0) {
                    JOptionPane.showMessageDialog(null, "Запись с ID " + id + " успешно обновлена.");
                    refreshTrainingsTable(); // Обновляем таблицу
                } else if (updateResult == -2) {
                    JOptionPane.showMessageDialog(null, "Ошибка: база данных training_schedule не существует.");
                } else if (updateResult == -1) {
                    JOptionPane.showMessageDialog(null, "Ошибка: таблица trainings не существует.");
                } else if (updateResult == -9) {
                    JOptionPane.showMessageDialog(null, "Ошибка: Некорректный ID.");
                } else if (updateResult == -10) {
                    JOptionPane.showMessageDialog(null, "Ошибка: Нет данных для обновления.");
                } else if (updateResult == 0) {
                    JOptionPane.showMessageDialog(null, "Запись с ID " + id + " не найдена.");
                } else {
                    JOptionPane.showMessageDialog(null, "Ошибка при обновлении записи.");
                }
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(null, "Ошибка ввода данных: " + ex.getMessage());
            }
        }
    }

    // Добавление записи
    private void openAddTrainingDialog(String username, String password) {
        JTextField titleField = new JTextField();
        JTextField dateField = new JTextField();
        JTextField startTimeField = new JTextField();
        JTextField durationField = new JTextField();
        JTextField maxParticipantsField = new JTextField();
        JTextField currentParticipantsField = new JTextField();
        JTextField difficultyLevelField = new JTextField();
        JTextField trainerNameField = new JTextField();
        JTextField priceField = new JTextField();

        // Панель для размещения полей
        JPanel panel = new JPanel(new GridLayout(0, 2));
        panel.add(new JLabel("Название:"));
        panel.add(titleField);
        panel.add(new JLabel("Дата (гггг-мм-дд):"));
        panel.add(dateField);
        panel.add(new JLabel("Время начала (чч:мм:сс):"));
        panel.add(startTimeField);
        panel.add(new JLabel("Длительность:"));
        panel.add(durationField);
        panel.add(new JLabel("Макс. участников:"));
        panel.add(maxParticipantsField);
        panel.add(new JLabel("Текущие участники:"));
        panel.add(currentParticipantsField);
        panel.add(new JLabel("Уровень сложности:"));
        panel.add(difficultyLevelField);
        panel.add(new JLabel("Имя тренера:"));
        panel.add(trainerNameField);
        panel.add(new JLabel("Цена:"));
        panel.add(priceField);

        int result = JOptionPane.showConfirmDialog(null, panel, "Добавить тренировку", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            try {
                String title = titleField.getText();
                Date date = Date.valueOf(dateField.getText()); // Преобразуем строку в Date
                Time startTime = Time.valueOf(startTimeField.getText()); // Преобразуем строку в Time
                String duration = durationField.getText();
                int maxParticipants = Integer.parseInt(maxParticipantsField.getText());
                int currentParticipants = Integer.parseInt(currentParticipantsField.getText());
                String difficultyLevel = difficultyLevelField.getText();
                String trainerName = trainerNameField.getText();
                double price = Double.parseDouble(priceField.getText());

                String resultMessage = addTraining("training_schedule", title, date, startTime, duration,
                        maxParticipants, currentParticipants, difficultyLevel, trainerName, price, username, password);

                JOptionPane.showMessageDialog(null, resultMessage);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(null, "Ошибка ввода данных: " + ex.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TrainingsManager());
    }
}

