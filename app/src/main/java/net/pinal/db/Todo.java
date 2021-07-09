package net.pinal.db;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

import java.io.Serializable;

@Entity(tableName = MyDatabase.TABLE_NAME_TODO)
public class Todo implements Serializable {
    @PrimaryKey(autoGenerate = true)
    public int todo_id;
    public String artistId;
    public String artistName;
    public String artistGenre;
    public String time;
    public String uploaded;

}
