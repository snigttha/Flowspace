package com.flowspace.dao;

import java.util.List;

/**
 * Generic DAO interface implemented by all data access objects in FlowSpace.
 * 
 * @param <T> The entity type extending WorkspaceItem or domain object.
 */
public interface Dao<T> {
    /**
     * Retrieve an item by primary ID, strictly filtered by user_id for security.
     */
    T findById(int id, int userId);

    /**
     * Retrieve all items belonging to a user.
     */
    List<T> findAll(int userId);

    /**
     * Persist a new entity.
     */
    T create(T item);

    /**
     * Update an existing entity.
     */
    boolean update(T item);

    /**
     * Delete an entity by ID, strictly filtered by user_id.
     */
    boolean delete(int id, int userId);
}
