package net.pinal.db;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

@Dao
public interface DaoAccess {

    @Insert
    long insertTodo(Todo todo);

    @Insert
    void insertTodoList(List<Todo> todoList);

    @Query("SELECT * FROM " + MyDatabase.TABLE_NAME_TODO)
    List<Todo> fetchAllTodos();


    @Query("SELECT * FROM " + MyDatabase.TABLE_NAME_TODO + " WHERE time = :time")
    int isDataExist(String time);

    /*@Query("SELECT * FROM " + MyDatabase.TABLE_NAME_TODO + " WHERE todo_id = :todoId")
    Todo fetchTodoListById(int todoId);*/

    @Query("UPDATE " + MyDatabase.TABLE_NAME_TODO + "  SET uploaded=:uploaded WHERE todo_id=:id")
    int updateTodo(String id,String uploaded);

}
