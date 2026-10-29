package com.flowspace.dao;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Generic Data Access Object interface.
 *
 * <p>Every module's DAO implements this interface (Notes, Tasks, MoodEntry,
 * PeriodLog). PLAN.md requires a "generic {@code Dao<T>} interface,
 * implemented by every DAO".
 *
 * @param <T> the domain type managed by this DAO
 *
 * @author Member 2 – Notes module (interface shared with all members)
 */
public interface Dao<T> {

    /**
     * Persists a new item and returns it with its generated id set.
     *
     * @param item the item to save
     * @return the saved item (with db-assigned id and timestamps)
     * @throws SQLException on any database error
     */
    T create(T item) throws SQLException;

    /**
     * Returns the item with the given id belonging to the given user,
     * or an empty Optional when no such item exists.
     *
     * @param id     primary key
     * @param userId the owner; enforces row-level privacy
     */
    Optional<T> findById(int id, int userId) throws SQLException;

    /**
     * Returns all items belonging to the given user, newest first.
     */
    List<T> findAll(int userId) throws SQLException;

    /**
     * Applies the given field updates to the item identified by id/userId.
     *
     * @return the updated item, or empty if not found / not owned
     */
    Optional<T> update(T item) throws SQLException;

    /**
     * Deletes the item identified by id, only when it belongs to userId.
     *
     * @return true if a row was actually deleted
     */
    boolean delete(int id, int userId) throws SQLException;
}
