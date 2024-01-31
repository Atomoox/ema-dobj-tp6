package org.ema.imanip;

import javax.persistence.*;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.CriteriaUpdate;
import javax.persistence.metamodel.Metamodel;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.sql.Statement;


public class EntityManagerImpl implements EntityManager{
    private static final String DATABASE_URL = "jdbc:hsqldb:mem:testdb";
    private static final String DATABASE_USER = "sa";
    private static final String DATABASE_PASSWORD = "";

    private String getSqlTypeFromJavaTypes(String type) {
        switch (type) {
            case "long":
                return "BIGINT";
            case "int":
                return "INT";
            case "java.lang.String":
                return "VARCHAR(255)";
            case "double":
                return "DOUBLE";
        }

       // @todo ADD ALL THE TYPES ???
        return "VARCHAR(255)";

    }

    private <T> String generateQueryFromClassFields(Class<T> entityClass) {
        String[] formattedFields = Arrays.stream(entityClass.getDeclaredFields())
                .map(field -> String.format(
                        "%s %s %s",
                        field.getName(),
                        getSqlTypeFromJavaTypes(field.getType().getName()),
                        field.getName().equals("id") ? "IDENTITY PRIMARY KEY" : ""
                ))
                .toArray(String[]::new);

        String fields = String.join(", ", formattedFields);

        return String.format(
                "%s (%s)",
                entityClass.getSimpleName(),
                fields
        );
    }

    private <T> void generateTable(Class<T> entityClass) {
        String query = generateQueryFromClassFields(entityClass);
        try (Connection connection = DriverManager.getConnection(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD)) {
            PreparedStatement createTableStatement = connection.prepareStatement(
                    String.format(
                            "CREATE TABLE IF NOT EXISTS %s",
                            query
                    )
            );

            createTableStatement.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void persist (Object entity) {
        generateTable(entity.getClass());

        String tableName = entity.getClass().getSimpleName();
        String[] fieldNames = Arrays.stream(entity.getClass().getDeclaredFields())
                .filter(field -> !field.getName().equals("id"))
                .map(Field::getName)
                .toArray(String[]::new);
        String[] fieldValues = Arrays.stream(entity.getClass().getDeclaredFields())
                .filter(field -> !field.getName().equals("id"))
                .map(field -> {
                    try {
                        field.setAccessible(true);
                        return String.valueOf(field.get(entity));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                        return null;
                    }
                })
                .toArray(String[]::new);

        try (Connection connection = DriverManager.getConnection(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD)) {
            String insertQuery = String.format(
                    "INSERT INTO %s (%s) VALUES (%s)",
                    tableName,
                    String.join(", ", fieldNames),
                    String.join(", ", Arrays.stream(fieldValues).map(value -> "'" + value + "'").toArray(String[]::new))
            );

            PreparedStatement insertStatement = connection.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS);
            insertStatement.execute();

            ResultSet generatedKeys = insertStatement.getGeneratedKeys();

            while (generatedKeys.next()) {
                long generatedId = generatedKeys.getLong(1);
                Field idField = entity.getClass().getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(entity, generatedId);
            }
        } catch (SQLException | NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public <T> T merge (T entity) {
        generateTable(entity.getClass());

        String objectId = null;

        try {
            Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            objectId = String.valueOf(idField.get(entity));
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
            return null;
        }

        String tableName = entity.getClass().getSimpleName();
        String[] fieldNameAndValues = Arrays.stream(entity.getClass().getDeclaredFields())
                .map(field -> {
                    try {
                        field.setAccessible(true);
                        String fieldName = field.getName();
                        String fieldValue;

                        if (fieldName.equals("version")) {
                            fieldValue = String.valueOf((int) field.get(entity) + 1);
                        } else {
                            fieldValue = String.valueOf(field.get(entity));
                        }

                        if (getSqlTypeFromJavaTypes(field.getType().getSimpleName()).equals("VARCHAR(255)")) {
                            return String.format("%s='%s'", fieldName, fieldValue);
                        }
                        return String.format("%s=%s", fieldName, fieldValue);
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                        return null;
                    }
                })
                .toArray(String[]::new);



        try (Connection connection = DriverManager.getConnection(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD)) {
            String insertQuery = String.format(
                    "UPDATE %s SET %s WHERE id=%s",
                    tableName,
                    String.join(", ", fieldNameAndValues),
                    objectId
            );

            PreparedStatement insertStatement = connection.prepareStatement(insertQuery);
            insertStatement.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return entity;
    }

    public <T> T find (Class<T> entityClass, Object primaryKey) {
        generateTable(entityClass);
        String tableName = entityClass.getSimpleName();

        try (Connection connection = DriverManager.getConnection(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD)) {
            String selectQuery = String.format(
                    "SELECT * FROM %s WHERE id = ?",
                    tableName
            );

            PreparedStatement selectStatement = connection.prepareStatement(selectQuery);
            selectStatement.setObject(1, primaryKey);

            ResultSet resultSet = selectStatement.executeQuery();
            if (resultSet.next()) {
                T entity =  entityClass.getConstructor().newInstance();

                // Assuming there are getter and setter methods for each field in the entity class
                Arrays.stream(entityClass.getDeclaredFields())
                        .forEach(field -> {
                            try {
                                field.setAccessible(true);
                                String fieldName = field.getName();
                                Class<?> fieldType = field.getType();

                                // Handle different data types accordingly
                                if (fieldType.equals(int.class)) {
                                    field.set(entity, resultSet.getInt(fieldName));
                                } else if (fieldType.equals(long.class)) {
                                    field.set(entity, resultSet.getLong(fieldName));
                                } else if (fieldType.equals(String.class)) {
                                    field.set(entity, resultSet.getString(fieldName));
                                } else if (fieldType.equals(double.class)) {
                                    field.set(entity, resultSet.getDouble(fieldName));
                                }
                                // Add more cases for other data types if needed
                            } catch (IllegalAccessException | SQLException e) {
                                e.printStackTrace();
                            }
                        });

                return entity;
            }
        } catch (SQLException | NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }

        return null;
    }

    public void remove(Object entity) {}

    @Override
    public <T> T find(Class<T> entityClass, Object primaryKey, Map<String, Object> properties) {
        return null;
    }

    @Override
    public <T> T find(Class<T> entityClass, Object primaryKey, LockModeType lockMode) {
        return null;
    }

    @Override
    public <T> T find(Class<T> entityClass, Object primaryKey, LockModeType lockMode, Map<String, Object> properties) {
        return null;
    }

    @Override
    public <T> T getReference(Class<T> entityClass, Object primaryKey) {
        return null;
    }

    @Override
    public void flush() {

    }

    @Override
    public void setFlushMode(FlushModeType flushMode) {

    }

    @Override
    public FlushModeType getFlushMode() {
        return null;
    }

    @Override
    public void lock(Object entity, LockModeType lockMode) {

    }

    @Override
    public void lock(Object entity, LockModeType lockMode, Map<String, Object> properties) {

    }

    @Override
    public void refresh(Object entity) {

    }

    @Override
    public void refresh(Object entity, Map<String, Object> properties) {

    }

    @Override
    public void refresh(Object entity, LockModeType lockMode) {

    }

    @Override
    public void refresh(Object entity, LockModeType lockMode, Map<String, Object> properties) {

    }

    @Override
    public void clear() {

    }

    @Override
    public void detach(Object entity) {

    }

    @Override
    public boolean contains(Object entity) {
        return false;
    }

    @Override
    public LockModeType getLockMode(Object entity) {
        return null;
    }

    @Override
    public void setProperty(String propertyName, Object value) {

    }

    @Override
    public Map<String, Object> getProperties() {
        return null;
    }

    @Override
    public Query createQuery(String qlString) {
        return null;
    }

    @Override
    public <T> TypedQuery<T> createQuery(CriteriaQuery<T> criteriaQuery) {
        return null;
    }

    @Override
    public Query createQuery(CriteriaUpdate updateQuery) {
        return null;
    }

    @Override
    public Query createQuery(CriteriaDelete deleteQuery) {
        return null;
    }

    @Override
    public <T> TypedQuery<T> createQuery(String qlString, Class<T> resultClass) {
        return null;
    }

    @Override
    public Query createNamedQuery(String name) {
        return null;
    }

    @Override
    public <T> TypedQuery<T> createNamedQuery(String name, Class<T> resultClass) {
        return null;
    }

    @Override
    public Query createNativeQuery(String sqlString) {
        return null;
    }

    @Override
    public Query createNativeQuery(String sqlString, Class resultClass) {
        return null;
    }

    @Override
    public Query createNativeQuery(String sqlString, String resultSetMapping) {
        return null;
    }

    @Override
    public StoredProcedureQuery createNamedStoredProcedureQuery(String name) {
        return null;
    }

    @Override
    public StoredProcedureQuery createStoredProcedureQuery(String procedureName) {
        return null;
    }

    @Override
    public StoredProcedureQuery createStoredProcedureQuery(String procedureName, Class... resultClasses) {
        return null;
    }

    @Override
    public StoredProcedureQuery createStoredProcedureQuery(String procedureName, String... resultSetMappings) {
        return null;
    }

    @Override
    public void joinTransaction() {

    }

    @Override
    public boolean isJoinedToTransaction() {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> cls) {
        return null;
    }

    @Override
    public Object getDelegate() {
        return null;
    }

    @Override
    public void close() {

    }

    @Override
    public boolean isOpen() {
        return false;
    }

    @Override
    public EntityTransaction getTransaction() {
        return null;
    }

    @Override
    public EntityManagerFactory getEntityManagerFactory() {
        return null;
    }

    @Override
    public CriteriaBuilder getCriteriaBuilder() {
        return null;
    }

    @Override
    public Metamodel getMetamodel() {
        return null;
    }

    @Override
    public <T> EntityGraph<T> createEntityGraph(Class<T> rootType) {
        return null;
    }

    @Override
    public EntityGraph<?> createEntityGraph(String graphName) {
        return null;
    }

    @Override
    public EntityGraph<?> getEntityGraph(String graphName) {
        return null;
    }

    @Override
    public <T> List<EntityGraph<? super T>> getEntityGraphs(Class<T> entityClass) {
        return null;
    }

}
